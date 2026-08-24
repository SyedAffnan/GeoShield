package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.geo.StateBoundaryIndex;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Exercises the whole approved chain with real collaborators:
 * GPS -> GeographicResolutionService -> State/UT -> MoRTH geographicUnit ->
 * HistoricalRiskFeatureService -> RiskContextAssembler -> BaselineRiskFusionService.
 *
 * <p>Only the module service boundaries that need infrastructure (Location, Incident,
 * Identity, Historical Data, and the risk-score repository) are stubbed. The
 * geographic resolution, historical normalization, assembly, and fusion are all real.
 * The historical values below are the actual MoRTH 2024 per-lakh figures.
 */
@ExtendWith(MockitoExtension.class)
class GeographicHistoricalRiskIntegrationTest {
    private static final String MORTH_SOURCE = "MoRTH Road Accidents in India 2024";
    private static final String PER_LAKH_2024 =
            "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024";
    private static final String CATEGORY =
            "State / UT - wise Total Number of Persons Injured in Road Accidents";

    // Bengaluru, Karnataka.
    private static final BigDecimal LATITUDE = new BigDecimal("12.9716");
    private static final BigDecimal LONGITUDE = new BigDecimal("77.5946");

    private static StateBoundaryIndex boundaryIndex;

    @Mock private HistoricalDataService historicalDataService;
    @Mock private LocationService locationService;
    @Mock private IncidentService incidentService;
    @Mock private IdentityService identityService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private User user;

    private UUID userId;
    private RiskContextAssembler assembler;

    @BeforeAll
    static void loadBoundariesOnce() {
        boundaryIndex = new StateBoundaryIndex();
    }

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        assembler = new RiskContextAssembler(locationService, incidentService,
                new BoundaryGeographicResolutionService(boundaryIndex),
                new HistoricalRiskFeatureService(historicalDataService),
                new IncidentRiskFeatureService(),
                new TimeOfDayRiskService(Clock.fixed(Instant.parse("2026-08-24T10:00:00Z"), ZoneOffset.UTC)));
        lenient().when(incidentService.getIncidents(userId)).thenReturn(List.of());
        lenient().when(identityService.getUserById(userId)).thenReturn(user);
    }

    private void storedLocationIs(BigDecimal latitude, BigDecimal longitude) {
        when(locationService.getCurrentLocation(userId))
                .thenReturn(new LocationResponse(1L, latitude, longitude, null, null, Instant.now()));
    }

    /** The actual MoRTH 2024 per-lakh figures; Kerala at 152.6 is the real maximum. */
    private void morthPerLakhRecordsAreAvailable() {
        when(historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT)).thenReturn(List.of(
                perLakh("Kerala", "152.6"), perLakh("Ladakh", "107.0"), perLakh("Tamil Nadu", "92.7"),
                perLakh("Karnataka", "77.2"), perLakh("Goa", "65.8"), perLakh("Maharashtra", "24.0"),
                perLakh("Delhi", "24.0"), perLakh("Bihar", "5.5")));
    }

    @Test
    void resolvedStateUtLooksUpItsMorthRecordAndMakesTheHistoricalFactorAvailable() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        // Karnataka 77.2 / Kerala 152.6 * 100, at the existing scale of 8, HALF_UP.
        assertTrue(context.historicalIncidentRisk().available());
        assertEquals(new BigDecimal("50.58977720"), context.historicalIncidentRisk().normalizedRisk());
        assertNull(context.historicalIncidentRisk().unavailabilityReason());
    }

    @Test
    void carriesTheActualMorthProvenanceAndNormalizationWithoutClaimingTouristSpecificity() {
        morthPerLakhRecordsAreAvailable();
        var resolution = new BoundaryGeographicResolutionService(boundaryIndex).resolve(LATITUDE, LONGITUDE);
        assertEquals("Karnataka", resolution.geographicUnit());

        var feature = new HistoricalRiskFeatureService(historicalDataService).historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.HISTORICAL_INCIDENT, feature.factor());
        assertEquals(MORTH_SOURCE, feature.source());
        assertEquals("metricValue / maximum same-metric State/UT value × 100", feature.normalization());
        assertTrue(feature.reason().contains("not tourist-specific"));
    }

    @Test
    void producesARealNonZeroLowScoreFromTheHistoricalFactorAloneWithoutFabricatingOthers() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();
        var fusion = new BaselineRiskFusionService(approvedProperties(), identityService,
                riskScoreRepository, new ObjectMapper());

        BaselineRiskResult result = fusion.calculateBaselineRisk(assembler.assembleForCurrentUser(userId));

        // 50.58977720 * 0.30 = 15.1769331600, and nothing else contributes.
        assertEquals(0, result.score().compareTo(new BigDecimal("15.1769331600")));
        assertEquals(RiskLevel.LOW, result.riskLevel());

        var historical = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.HISTORICAL_INCIDENT).findFirst().orElseThrow();
        assertTrue(historical.available());
        assertEquals(0, historical.weight().compareTo(new BigDecimal("0.30")));
        assertTrue(historical.contribution().signum() > 0, "the historical contribution must be genuinely non-zero");

        // Every other factor stays explicitly unavailable and contributes exactly zero.
        assertTrue(result.contributingFactors().stream()
                .filter(factor -> factor.factor() != RiskFactorType.HISTORICAL_INCIDENT)
                .allMatch(factor -> !factor.available() && factor.normalizedRisk() == null
                        && factor.contribution().signum() == 0));

        ArgumentCaptor<RiskScore> saved = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(saved.capture());
        assertEquals(15, saved.getValue().getScore());
    }

    @Test
    void keepsTheHistoricalFactorUnavailableWhenTheCoordinateResolvesToNoStateUt() {
        // Mid Bay of Bengal: a valid coordinate that is inside no State/UT polygon.
        storedLocationIs(new BigDecimal("15.0"), new BigDecimal("85.0"));

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        assertFalse(context.historicalIncidentRisk().available());
        assertNull(context.historicalIncidentRisk().normalizedRisk());
        assertTrue(context.historicalIncidentRisk().unavailabilityReason()
                .contains("No India State/UT boundary contains"));
    }

    @Test
    void keepsTheHistoricalFactorUnavailableWhenTheResolvedStateUtHasNoMatchingMorthRecord() {
        storedLocationIs(LATITUDE, LONGITUDE);
        // Karnataka resolves correctly, but no Karnataka metric was imported.
        when(historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT))
                .thenReturn(List.of(perLakh("Kerala", "152.6"), perLakh("Bihar", "5.5")));

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        assertFalse(context.historicalIncidentRisk().available());
        assertNull(context.historicalIncidentRisk().normalizedRisk());
        assertTrue(context.historicalIncidentRisk().unavailabilityReason().contains("No supported State/UT"));
    }

    @Test
    void resolvesEachStateUtToTheNameItsOwnMorthRecordIsFiledUnder() {
        morthPerLakhRecordsAreAvailable();
        var resolver = new BoundaryGeographicResolutionService(boundaryIndex);
        var featureService = new HistoricalRiskFeatureService(historicalDataService);

        // Chennai -> Tamil Nadu -> 92.7 / 152.6 * 100, and Mumbai -> Maharashtra -> 24.0 / 152.6 * 100.
        var chennai = featureService.historicalIncidentRisk(
                resolver.resolve(new BigDecimal("13.0827"), new BigDecimal("80.2707")));
        assertTrue(chennai.available());
        assertEquals(new BigDecimal("60.74705111"), chennai.value());

        var mumbai = featureService.historicalIncidentRisk(
                resolver.resolve(new BigDecimal("19.0760"), new BigDecimal("72.8777")));
        assertTrue(mumbai.available());
        assertEquals(new BigDecimal("15.72739187"), mumbai.value());
    }

    private HistoricalSafetyRecordSummary perLakh(String geographicUnit, String value) {
        return new HistoricalSafetyRecordSummary(MORTH_SOURCE, 2024, GeographicLevel.STATE_UT,
                geographicUnit, CATEGORY, PER_LAKH_2024, new BigDecimal(value), false);
    }

    private RiskFusionProperties approvedProperties() {
        return new RiskFusionProperties(new BigDecimal("0.30"), new BigDecimal("0.20"), new BigDecimal("0.15"),
                new BigDecimal("0.15"), new BigDecimal("0.10"), new BigDecimal("0.05"), new BigDecimal("0.05"),
                39, 59, 79);
    }
}
