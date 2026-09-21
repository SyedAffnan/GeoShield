package com.geoshield.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.geo.StateBoundaryIndex;
import com.geoshield.risk.repository.RiskScoreRepository;
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import com.geoshield.risk.weather.MorthWeatherSeverityTable;
import com.geoshield.risk.weather.WeatherObservation;
import com.geoshield.risk.weather.WeatherObservationProvider;
import com.geoshield.risk.weather.WeatherObservationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * End-to-end integration test validating real risk computation and audit persistence.
 *
 * <p>Crucially, {@link BaselineRiskFusionService} and {@link RiskContextAssembler} are NOT mocked.
 * Real feature services, real geographic resolution, real fusion calculations, and real
 * {@link RiskApiServiceImpl} execution are exercised together, verifying that unique decisionId
 * generation, baseline calculation, SACHET promotion, and audit record details are properly passed
 * to {@link RiskScoreRepository#save(RiskScore)}.
 */
@ExtendWith(MockitoExtension.class)
class RiskAuditPersistenceIntegrationTest {

    // Bengaluru, Karnataka coordinates
    private static final BigDecimal LATITUDE = new BigDecimal("12.9716");
    private static final BigDecimal LONGITUDE = new BigDecimal("77.5946");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-24T10:00:00Z");

    private static StateBoundaryIndex boundaryIndex;

    @Mock private HistoricalDataService historicalDataService;
    @Mock private LocationService locationService;
    @Mock private IncidentService incidentService;
    @Mock private SachetAlertService sachetAlertService;
    @Mock private EmergencyServicesService emergencyServicesService;
    @Mock private IdentityService identityService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private User user;

    private ObjectMapper objectMapper;
    private BaselineRiskFusionService realRiskFusionService;
    private RiskApiServiceImpl realRiskApiService;
    private UUID userId;
    private Map<UUID, RiskScore> storedScores;

    @BeforeAll
    static void loadBoundariesOnce() {
        boundaryIndex = new StateBoundaryIndex();
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        objectMapper = new ObjectMapper();
        storedScores = new ConcurrentHashMap<>();

        // Repository contract: store and retrieve by decisionId
        lenient().when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(invocation -> {
            RiskScore s = invocation.getArgument(0);
            storedScores.put(s.getDecisionId(), s);
            return s;
        });
        lenient().when(riskScoreRepository.findByDecisionId(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return Optional.ofNullable(storedScores.get(id));
        });

        // Identity contract
        lenient().when(identityService.getUserById(userId)).thenReturn(user);

        // Location contract
        lenient().when(locationService.getCurrentLocation(userId)).thenReturn(
                new LocationResponse(1L, LATITUDE, LONGITUDE, BigDecimal.valueOf(10.0), BigDecimal.ZERO, FIXED_INSTANT)
        );

        // Incident and emergency service mocks
        lenient().when(incidentService.getActiveIncidents()).thenReturn(List.of());
        lenient().when(emergencyServicesService.findNearestFacility(anyDouble(), anyDouble())).thenReturn(Optional.empty());

        // Historical data mock for Karnataka
        lenient().when(historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT)).thenReturn(List.of(
                new HistoricalSafetyRecordSummary(
                        "MoRTH Road Accidents in India 2024",
                        2024,
                        GeographicLevel.STATE_UT,
                        "Kerala",
                        "State / UT - wise Total Number of Persons Injured in Road Accidents",
                        "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024",
                        new BigDecimal("152.6"),
                        false
                ),
                new HistoricalSafetyRecordSummary(
                        "MoRTH Road Accidents in India 2024",
                        2024,
                        GeographicLevel.STATE_UT,
                        "Karnataka",
                        "State / UT - wise Total Number of Persons Injured in Road Accidents",
                        "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024",
                        new BigDecimal("77.2"),
                        false
                )
        ));

        // Real feature services with fixed weather observation
        FixedWeatherObservationProvider weatherProvider = new FixedWeatherObservationProvider(
                WeatherObservationResult.observed(new WeatherObservation("Open-Meteo", 61, FIXED_INSTANT))
        );

        RiskContextAssembler realAssembler = new RiskContextAssembler(
                locationService,
                incidentService,
                new BoundaryGeographicResolutionService(boundaryIndex),
                new HistoricalRiskFeatureService(historicalDataService),
                new IncidentRiskFeatureService(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)),
                new TimeOfDayRiskService(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), new MorthTimeOfDayDistribution()),
                new WeatherRiskService(weatherProvider, new MorthWeatherSeverityTable()),
                new EmergencyServiceProximityRiskService(emergencyServicesService)
        );

        // Real fusion properties (frozen Step 1 / Step 2 weights)
        RiskFusionProperties fusionProperties = new RiskFusionProperties(
                new BigDecimal("0.30"),
                new BigDecimal("0.20"),
                new BigDecimal("0.15"),
                new BigDecimal("0.15"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.05"),
                39, 59, 79
        );

        // Real BaselineRiskFusionService (NOT MOCKED)
        realRiskFusionService = new BaselineRiskFusionService(
                fusionProperties,
                identityService,
                riskScoreRepository,
                objectMapper
        );

        // Real RiskApiServiceImpl (NOT MOCKED)
        realRiskApiService = new RiskApiServiceImpl(
                realAssembler,
                realRiskFusionService,
                locationService,
                sachetAlertService,
                riskScoreRepository,
                identityService,
                objectMapper
        );
    }

    @Test
    @DisplayName("End-to-end: computes baseline risk and updates audit persistence with SACHET CRITICAL override")
    void computesBaselineAndPersistsSachetCriticalOverride() {
        SachetAlertSummary severeAlert = new SachetAlertSummary(
                UUID.randomUUID(),
                "NDMA-2026-CYC-0042",
                "alert@sachet.ndma.gov.in",
                FIXED_INSTANT,
                "Met",
                "Severe Cyclonic Storm Warning",
                "Immediate",
                "Extreme",
                "Observed",
                FIXED_INSTANT.minus(1, ChronoUnit.HOURS),
                FIXED_INSTANT.plus(5, ChronoUnit.HOURS),
                "Severe Cyclone Warning",
                "Evacuate coastal and low-lying areas",
                "Seek immediate emergency shelter",
                "Karnataka Coastal",
                false
        );

        when(sachetAlertService.findApplicableActiveAlert(eq(LATITUDE.doubleValue()), eq(LONGITUDE.doubleValue()), any(Instant.class)))
                .thenReturn(Optional.of(severeAlert));
        when(sachetAlertService.isQualifyingSevereAlert(severeAlert)).thenReturn(true);

        RiskResponse response = realRiskApiService.getCurrentRisk(userId);

        // 1. Verify API response
        assertNotNull(response);
        assertNotNull(response.decisionId());
        assertTrue(response.overrideActive());
        assertEquals(RiskLevel.CRITICAL, response.effectiveRiskLevel());
        // Baseline level preserved in response.riskLevel()
        assertNotNull(response.riskLevel());
        assertFalse(response.riskLevel() == RiskLevel.CRITICAL); // Baseline score is not critical

        // 2. Verify repository persistence: storedScore must exist and be updated
        RiskScore persisted = storedScores.get(response.decisionId());
        assertNotNull(persisted, "RiskScore must be saved in repository under the generated decisionId");
        assertEquals(response.decisionId(), persisted.getDecisionId());
        assertTrue(persisted.isOverrideActive());
        assertEquals(RiskLevel.CRITICAL, persisted.getEffectiveRiskLevel());
        assertEquals(response.riskLevel(), persisted.getRiskLevel()); // Baseline preserved
        assertEquals("NDMA-2026-CYC-0042", persisted.getSachetAlertIdentifier());
        assertEquals(GeographicLevel.STATE_UT, persisted.getGeographicLevel());
        assertEquals("Karnataka", persisted.getGeographicUnit());
        assertEquals("29", persisted.getStateCode());

        // 3. Verify audit json columns
        assertNotNull(persisted.getContributingFactors());
        assertThat(persisted.getContributingFactors()).contains("HISTORICAL_INCIDENT");
        assertNotNull(persisted.getDataCompleteness());
        assertThat(persisted.getDataCompleteness()).contains("availableFactorCount");
        assertNotNull(persisted.getFactorDetails());
        assertThat(persisted.getFactorDetails()).contains("HISTORICAL_INCIDENT");
    }

    @Test
    @DisplayName("End-to-end: computes standard baseline and persists truthfully when no severe alert active")
    void computesBaselineAndPersistsStandardDecisionWithoutOverride() {
        when(sachetAlertService.findApplicableActiveAlert(eq(LATITUDE.doubleValue()), eq(LONGITUDE.doubleValue()), any(Instant.class)))
                .thenReturn(Optional.empty());

        RiskResponse response = realRiskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertNotNull(response.decisionId());
        assertFalse(response.overrideActive());
        assertEquals(response.riskLevel(), response.effectiveRiskLevel());

        RiskScore persisted = storedScores.get(response.decisionId());
        assertNotNull(persisted);
        assertEquals(response.decisionId(), persisted.getDecisionId());
        assertFalse(persisted.isOverrideActive());
        assertEquals(response.riskLevel(), persisted.getEffectiveRiskLevel());
        assertEquals("Karnataka", persisted.getGeographicUnit());
        assertEquals("29", persisted.getStateCode());
    }

    private record FixedWeatherObservationProvider(WeatherObservationResult result)
            implements WeatherObservationProvider {

        @Override
        public WeatherObservationResult currentWeather(BigDecimal latitude, BigDecimal longitude) {
            return result;
        }

        @Override
        public String providerName() {
            return "Open-Meteo";
        }
    }
}
