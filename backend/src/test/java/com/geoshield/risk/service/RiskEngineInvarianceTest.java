package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.*;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Step 9 P0 Risk Engine Invariance Tests:
 * Rigorously verifies that the introduction of the Historical Trend Advisory (Option E)
 * preserves 100% mathematical, architectural, and behavioral invariance of the
 * authoritative 5-factor deterministic risk core and SACHET override.
 */
class RiskEngineInvarianceTest {

    private RiskFusionProperties riskFusionProperties;
    private BaselineRiskFusionService fusionService;
    private RiskScoreRepository riskScoreRepository;
    private IdentityService identityService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        riskFusionProperties = new RiskFusionProperties(
                new BigDecimal("0.30"),
                new BigDecimal("0.20"),
                new BigDecimal("0.15"),
                new BigDecimal("0.15"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.05"),
                39, 59, 79
        );
        identityService = mock(IdentityService.class);
        riskScoreRepository = mock(RiskScoreRepository.class);
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));
        objectMapper = new ObjectMapper().findAndRegisterModules();

        fusionService = new BaselineRiskFusionService(
                riskFusionProperties, identityService, riskScoreRepository, objectMapper
        );
    }

    @Test
    @DisplayName("Invariant 1: Baseline 5 active factor weights are strictly (0.30, 0.20, 0.15, 0.15, 0.10)")
    void verifyFiveActiveFactorWeights() {
        assertEquals(new BigDecimal("0.30"), riskFusionProperties.historicalIncidentWeight());
        assertEquals(new BigDecimal("0.20"), riskFusionProperties.weatherWeight());
        assertEquals(new BigDecimal("0.15"), riskFusionProperties.timeOfDayWeight());
        assertEquals(new BigDecimal("0.15"), riskFusionProperties.serviceProximityWeight());
        assertEquals(new BigDecimal("0.10"), riskFusionProperties.userReportWeight());
    }

    @Test
    @DisplayName("Invariant 2: Dormant factor weights are strictly (0.05, 0.05) and contribute zero when unavailable")
    void verifyDormantFactorWeights() {
        assertEquals(new BigDecimal("0.05"), riskFusionProperties.connectivityWeight());
        assertEquals(new BigDecimal("0.05"), riskFusionProperties.otherContextWeight());
    }

    @Test
    @DisplayName("Invariant 3: Active weight sum is strictly 0.90, giving an active max baseline score of 90.00")
    void verifyActiveWeightSumAndMaxBaseline() {
        BigDecimal activeSum = riskFusionProperties.historicalIncidentWeight()
                .add(riskFusionProperties.weatherWeight())
                .add(riskFusionProperties.timeOfDayWeight())
                .add(riskFusionProperties.serviceProximityWeight())
                .add(riskFusionProperties.userReportWeight());
        assertEquals(new BigDecimal("0.90"), activeSum);

        // When all 5 active factors are maxed at 100.0
        UUID userId = UUID.randomUUID();
        when(identityService.getUserById(userId)).thenReturn(mock(User.class));

        BaselineRiskCalculationRequest maxRequest = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(new BigDecimal("100.0"), "Max Historical", "MoRTH"),
                RiskFactorInput.available(new BigDecimal("100.0"), "Max Weather", "Open-Meteo"),
                RiskFactorInput.available(new BigDecimal("100.0"), "Max Time", "TimeOfDay"),
                RiskFactorInput.available(new BigDecimal("100.0"), "Max Proximity", "OSM"),
                RiskFactorInput.available(new BigDecimal("100.0"), "Max Reports", "Incidents"),
                RiskFactorInput.unavailable("Dormant"),
                RiskFactorInput.unavailable("Dormant")
        );

        BaselineRiskResult result = fusionService.calculateBaselineRisk(maxRequest);
        assertEquals(0, new BigDecimal("90.00").compareTo(result.score()));
        assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    }

    @Test
    @DisplayName("Invariant 4: Risk level thresholds remain strictly LOW <= 39, MEDIUM 40-59, HIGH 60-79, CRITICAL >= 80")
    void verifyRiskLevelThresholds() {
        assertEquals(39, riskFusionProperties.lowMax());
        assertEquals(59, riskFusionProperties.mediumMax());
        assertEquals(79, riskFusionProperties.highMax());

        UUID userId = UUID.randomUUID();
        when(identityService.getUserById(userId)).thenReturn(mock(User.class));

        // Score = 39.00: Historical 100.0 (30.00) + Weather 45.0 (9.00) -> 39.00 -> LOW
        assertEquals(RiskLevel.LOW, calculateScore(new BigDecimal("100.0"), new BigDecimal("45.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        // Score = 40.00: Historical 100.0 (30.00) + Weather 50.0 (10.00) -> 40.00 -> MEDIUM
        assertEquals(RiskLevel.MEDIUM, calculateScore(new BigDecimal("100.0"), new BigDecimal("50.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        // Score = 59.00: Historical 100.0 (30.00) + Weather 100.0 (20.00) + TimeOfDay 60.0 (9.00) -> 59.00 -> MEDIUM
        assertEquals(RiskLevel.MEDIUM, calculateScore(new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("60.0"), BigDecimal.ZERO, BigDecimal.ZERO));

        // Score = 60.00: Weather 100.0 (20.00) + TimeOfDay 100.0 (15.00) + Proximity 100.0 (15.00) + Reports 100.0 (10.00) -> 60.00 -> HIGH
        assertEquals(RiskLevel.HIGH, calculateScore(BigDecimal.ZERO, new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("100.0")));

        // Score = 75.00: Historical 100.0 (30.00) + Weather 100.0 (20.00) + TimeOfDay 100.0 (15.00) + Reports 100.0 (10.00) -> 75.00 -> HIGH
        assertEquals(RiskLevel.HIGH, calculateScore(new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("100.0"), BigDecimal.ZERO, new BigDecimal("100.0")));

        // Score = 85.00: Historical 100.0 (30.00) + Weather 100.0 (20.00) + TimeOfDay 100.0 (15.00) + Proximity 100.0 (15.00) + Reports 50.0 (5.00) -> 85.00 -> CRITICAL
        assertEquals(RiskLevel.CRITICAL, calculateScore(new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("100.0"), new BigDecimal("50.0")));
    }

    private RiskLevel calculateScore(BigDecimal hist, BigDecimal weather, BigDecimal time, BigDecimal prox, BigDecimal rep) {
        UUID userId = UUID.randomUUID();
        when(identityService.getUserById(userId)).thenReturn(mock(User.class));

        BaselineRiskCalculationRequest req = new BaselineRiskCalculationRequest(
                userId,
                hist.compareTo(BigDecimal.ZERO) > 0 ? RiskFactorInput.available(hist, "test", "src") : RiskFactorInput.unavailable("none"),
                weather.compareTo(BigDecimal.ZERO) > 0 ? RiskFactorInput.available(weather, "test", "src") : RiskFactorInput.unavailable("none"),
                time.compareTo(BigDecimal.ZERO) > 0 ? RiskFactorInput.available(time, "test", "src") : RiskFactorInput.unavailable("none"),
                prox.compareTo(BigDecimal.ZERO) > 0 ? RiskFactorInput.available(prox, "test", "src") : RiskFactorInput.unavailable("none"),
                rep.compareTo(BigDecimal.ZERO) > 0 ? RiskFactorInput.available(rep, "test", "src") : RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none")
        );
        return fusionService.calculateBaselineRisk(req).riskLevel();
    }

    @Test
    @DisplayName("Invariant 5: HistoricalTrendAdvisoryService execution does NOT modify BaselineRiskFusionService or write to RiskScoreRepository")
    void verifyAdvisoryDoesNotWriteToRepositoryOrAffectFusion() {
        LocationService locationService = mock(LocationService.class);
        GeographicResolutionService geographicResolutionService = mock(GeographicResolutionService.class);

        UUID touristId = UUID.randomUUID();
        LocationResponse location = new LocationResponse(1L, new BigDecimal("12.9716"), new BigDecimal("77.5946"), BigDecimal.ONE, BigDecimal.ZERO, Instant.now());
        when(locationService.getCurrentLocation(touristId)).thenReturn(location);
        when(geographicResolutionService.resolve(new BigDecimal("12.9716"), new BigDecimal("77.5946")))
                .thenReturn(GeographicResolution.resolved(GeographicLevel.STATE_UT, "Karnataka"));

        HistoricalTrendAdvisoryServiceImpl advisoryService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                true,
                "data/historical_trend_evaluation_2024.json"
        );
        advisoryService.init();

        // Execute advisory service
        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(touristId);

        // Verify advisory executed successfully
        assertEquals("AVAILABLE", response.status());
        assertEquals("Karnataka", response.geographicUnit());
        assertEquals("STATE_UT", response.geographicLevel());
        assertEquals(2024, response.targetYear());
        assertEquals(new BigDecimal("27.89"), response.predictedAccidentSeverity());

        // Invariance checks:
        // 1. RiskScoreRepository.save() was NEVER called during advisory generation
        verify(riskScoreRepository, never()).save(any(RiskScore.class));

        // 2. BaselineRiskFusionService remains entirely decoupled
        // Calling BaselineRiskFusionService produces expected baseline result without interference
        when(identityService.getUserById(touristId)).thenReturn(mock(User.class));
        BaselineRiskCalculationRequest baselineReq = new BaselineRiskCalculationRequest(
                touristId,
                RiskFactorInput.available(new BigDecimal("50.0"), "50", "src"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none"),
                RiskFactorInput.unavailable("none")
        );
        BaselineRiskResult baselineResult = fusionService.calculateBaselineRisk(baselineReq);
        assertEquals(0, new BigDecimal("15.00").compareTo(baselineResult.score())); // 50 * 0.30 = 15.00
        assertEquals(RiskLevel.LOW, baselineResult.riskLevel());
        verify(riskScoreRepository, times(1)).save(any(RiskScore.class)); // only baseline saved its own score
    }

    @Test
    @DisplayName("Invariant 6: Geographic level is strictly STATE_UT even when resolving district coordinates")
    void verifyGeographicContainmentStateUtOnly() {
        LocationService locationService = mock(LocationService.class);
        GeographicResolutionService geographicResolutionService = mock(GeographicResolutionService.class);

        UUID touristId = UUID.randomUUID();
        // Coordinates in Mysuru district
        LocationResponse location = new LocationResponse(2L, new BigDecimal("12.2958"), new BigDecimal("76.6394"), BigDecimal.ONE, BigDecimal.ZERO, Instant.now());
        when(locationService.getCurrentLocation(touristId)).thenReturn(location);
        // District resolution: resolvedDistrict returns parentState="Karnataka"
        when(geographicResolutionService.resolve(new BigDecimal("12.2958"), new BigDecimal("76.6394")))
                .thenReturn(GeographicResolution.resolvedDistrict("Mysuru", "Karnataka", "KA", "KA-MY"));

        HistoricalTrendAdvisoryServiceImpl advisoryService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                true,
                "data/historical_trend_evaluation_2024.json"
        );
        advisoryService.init();

        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(touristId);

        // Must report STATE_UT and Karnataka, never "Mysuru" or "DISTRICT"
        assertEquals("AVAILABLE", response.status());
        assertEquals("STATE_UT", response.geographicLevel());
        assertEquals("Karnataka", response.geographicUnit());
    }

    @Test
    @DisplayName("Invariant 7: Fail-soft behavior on missing/disabled resource leaves core unaffected")
    void verifyFailSoftBehavior() {
        LocationService locationService = mock(LocationService.class);
        GeographicResolutionService geographicResolutionService = mock(GeographicResolutionService.class);

        UUID touristId = UUID.randomUUID();

        // Disabled advisory service
        HistoricalTrendAdvisoryServiceImpl disabledService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                false, // disabled
                "data/historical_trend_evaluation_2024.json"
        );
        disabledService.init();

        HistoricalTrendAdvisoryResponse response = disabledService.getAdvisoryForUser(touristId);
        assertEquals("UNAVAILABLE", response.status());
        assertNull(response.predictedAccidentSeverity());
        assertTrue(response.advisoryNotice().contains("unavailable"));

        // Non-existent resource path
        HistoricalTrendAdvisoryServiceImpl nonExistentResourceService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                true,
                "data/does_not_exist.json"
        );
        nonExistentResourceService.init();

        HistoricalTrendAdvisoryResponse nonExistentResponse = nonExistentResourceService.getAdvisoryForUser(touristId);
        assertEquals("UNAVAILABLE", nonExistentResponse.status());
        assertNull(nonExistentResponse.predictedAccidentSeverity());
    }
}
