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
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
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
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import com.geoshield.risk.weather.MorthWeatherSeverityTable;
import com.geoshield.risk.weather.WeatherObservation;
import com.geoshield.risk.weather.WeatherObservationProvider;
import com.geoshield.risk.weather.WeatherObservationResult;
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
 * The historical values below are the actual MoRTH 2024 per-lakh figures, and the
 * time-of-day values are the actual MoRTH 2024 Table 7.3 figures loaded from the bundled
 * resource. The clock is fixed so the assertions are stable; system time is never modified.
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

    /** 10:00Z is 15:30 Indian local time, so MoRTH's 15:00 to 18:00 band applies. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-24T10:00:00Z");

    /** Table 7.3: 85,010 accidents in 15:00-18:00 relative to the 1,02,897 peak band. */
    private static final BigDecimal TIME_OF_DAY_NORMALIZED = new BigDecimal("82.61659718");

    /** WMO 61 is slight rain, which maps to MoRTH's published "Rainy" condition. */
    private static final int OBSERVED_WMO_CODE = 61;

    /** Table 3.8: 12,665 killed in 35,284 rainy accidents, against the most severe published row. */
    private static final BigDecimal WEATHER_NORMALIZED = new BigDecimal("79.06699039");

    private static final String PROVIDER = "Open-Meteo";

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
        assembler = assemblerObserving(WeatherObservationResult.observed(
                new WeatherObservation(PROVIDER, OBSERVED_WMO_CODE, FIXED_INSTANT)));
        lenient().when(incidentService.getActiveIncidents()).thenReturn(List.of());
        lenient().when(identityService.getUserById(userId)).thenReturn(user);
    }

    /**
     * Builds the whole real chain around one fixed weather outcome. Every collaborator except the
     * module boundaries is the production class, so the weather feature is produced by the same code
     * the application runs; only the outbound HTTP call is replaced.
     */
    private RiskContextAssembler assemblerObserving(WeatherObservationResult weather) {
        return new RiskContextAssembler(locationService, incidentService,
                new BoundaryGeographicResolutionService(boundaryIndex),
                new HistoricalRiskFeatureService(historicalDataService),
                new IncidentRiskFeatureService(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)),
                new TimeOfDayRiskService(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                        new MorthTimeOfDayDistribution()),
                new WeatherRiskService(new FixedWeatherObservationProvider(weather),
                        new MorthWeatherSeverityTable()));
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
    void producesARealNonZeroScoreFromTheAvailableFactorsWithoutFabricatingTheOthers() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();
        var fusion = new BaselineRiskFusionService(approvedProperties(), identityService,
                riskScoreRepository, new ObjectMapper());

        BaselineRiskResult result = fusion.calculateBaselineRisk(assembler.assembleForCurrentUser(userId));

        // 50.58977720 * 0.30 + 79.06699039 * 0.20 + 82.61659718 * 0.15
        // = 15.1769331600 + 15.8133980780 + 12.3924895770.
        assertEquals(0, result.score().compareTo(new BigDecimal("43.3828208150")));
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());

        var historical = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.HISTORICAL_INCIDENT).findFirst().orElseThrow();
        assertTrue(historical.available());
        assertEquals(0, historical.weight().compareTo(new BigDecimal("0.30")));
        // The historical contribution is untouched by the time-of-day and weather factors.
        assertEquals(0, historical.contribution().compareTo(new BigDecimal("15.1769331600")));

        var timeOfDay = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.TIME_OF_DAY).findFirst().orElseThrow();
        assertTrue(timeOfDay.available());
        assertEquals(0, timeOfDay.weight().compareTo(new BigDecimal("0.15")));
        assertEquals(0, timeOfDay.normalizedRisk().compareTo(TIME_OF_DAY_NORMALIZED));
        // The time-of-day contribution is unchanged by the new weather factor.
        assertEquals(0, timeOfDay.contribution().compareTo(new BigDecimal("12.3924895770")));

        var weather = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.WEATHER).findFirst().orElseThrow();
        assertTrue(weather.available());
        // The approved weight is used as configured; it is not adjusted to move the score.
        assertEquals(0, weather.weight().compareTo(new BigDecimal("0.20")));
        assertEquals(0, weather.normalizedRisk().compareTo(WEATHER_NORMALIZED));
        assertEquals(0, weather.contribution().compareTo(new BigDecimal("15.8133980780")));

        var userReport = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.USER_REPORT).findFirst().orElseThrow();
        assertTrue(userReport.available());
        assertEquals(0, userReport.weight().compareTo(new BigDecimal("0.10")));
        assertEquals(0, userReport.normalizedRisk().compareTo(BigDecimal.ZERO));
        assertEquals(0, userReport.contribution().compareTo(BigDecimal.ZERO));

        // The remaining 3 factors stay explicitly unavailable and contribute exactly zero.
        assertTrue(result.contributingFactors().stream()
                .filter(factor -> factor.factor() != RiskFactorType.HISTORICAL_INCIDENT
                        && factor.factor() != RiskFactorType.TIME_OF_DAY
                        && factor.factor() != RiskFactorType.WEATHER
                        && factor.factor() != RiskFactorType.USER_REPORT)
                .allMatch(factor -> !factor.available() && factor.normalizedRisk() == null
                        && factor.contribution().signum() == 0));

        ArgumentCaptor<RiskScore> saved = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(saved.capture());
        assertEquals(43, saved.getValue().getScore());
    }

    @Test
    void producesIncreasedRiskScoreWhenActiveNearbyIncidentExists() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();

        // 1 active Road hazard directly at tourist location, reported 0h ago: severity 25 * 1.0 * 1.0 * 1.0 = 25.0
        // Weight 0.10 -> contributes 2.50000000 points.
        // Base score was 43.3828208150 + 2.50000000 = 45.8828208150.
        IncidentResponse activeIncident = new IncidentResponse(UUID.randomUUID(), "Road hazard", "Active road hazard",
                LATITUDE, LONGITUDE, "REPORTED", "a".repeat(64), IncidentSourceType.USER_REPORTED, FIXED_INSTANT);
        when(incidentService.getActiveIncidents()).thenReturn(List.of(activeIncident));

        var fusion = new BaselineRiskFusionService(approvedProperties(), identityService,
                riskScoreRepository, new ObjectMapper());

        BaselineRiskResult result = fusion.calculateBaselineRisk(assembler.assembleForCurrentUser(userId));

        assertEquals(0, result.score().compareTo(new BigDecimal("45.8828208150")));
        assertEquals(RiskLevel.MEDIUM, result.riskLevel());

        var userReport = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.USER_REPORT).findFirst().orElseThrow();
        assertTrue(userReport.available());
        assertEquals(0, userReport.weight().compareTo(new BigDecimal("0.10")));
        assertEquals(0, userReport.normalizedRisk().compareTo(new BigDecimal("25.00000000")));
        assertEquals(0, userReport.contribution().compareTo(new BigDecimal("2.50000000")));
    }

    @Test
    void assemblesTheWeatherFeatureFromARealObservationMappedOntoThePublishedMorthTable() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        assertTrue(context.weatherRisk().available());
        assertEquals(0, context.weatherRisk().normalizedRisk().compareTo(WEATHER_NORMALIZED));
        assertNull(context.weatherRisk().unavailabilityReason());
    }

    @Test
    void carriesBothRealWeatherSourcesWithoutClaimingMorthPublishesAZeroToHundredScore() {
        var feature = new WeatherRiskService(
                new FixedWeatherObservationProvider(WeatherObservationResult.observed(
                        new WeatherObservation(PROVIDER, OBSERVED_WMO_CODE, FIXED_INSTANT))),
                new MorthWeatherSeverityTable()).currentRisk(LATITUDE, LONGITUDE);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.WEATHER, feature.factor());
        // Who observed the weather, and who published the risk relationship - never conflated.
        assertTrue(feature.source().contains(PROVIDER + " current weather observation"), feature.source());
        assertTrue(feature.source().contains("MoRTH Road Accidents in India 2024, Table 3.8"), feature.source());
        assertTrue(feature.reason().contains("Rainy"), feature.reason());
        assertTrue(feature.reason().contains("MoRTH publishes no 0-100 weather risk score"), feature.reason());
        assertTrue(feature.reason().contains("not tourist-specific"), feature.reason());
    }

    @Test
    void keepsTheWeatherFactorUnavailableWhenTheProviderCannotBeReached() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();
        var offline = assemblerObserving(WeatherObservationResult.unavailable(
                "The Open-Meteo weather provider could not be reached."));

        BaselineRiskCalculationRequest context = offline.assembleForCurrentUser(userId);

        assertFalse(context.weatherRisk().available());
        assertNull(context.weatherRisk().normalizedRisk());
        // A weather outage never degrades the factors that do have real data.
        assertTrue(context.historicalIncidentRisk().available());
        assertTrue(context.timeOfDayRisk().available());
    }

    @Test
    void keepsTheWeatherFactorUnavailableForAnObservationOutsideTheDocumentedMapping() {
        storedLocationIs(LATITUDE, LONGITUDE);
        // WMO 4 is smoke-reduced visibility, which MoRTH's published conditions do not cover.
        var unmapped = assemblerObserving(WeatherObservationResult.observed(
                new WeatherObservation(PROVIDER, 4, FIXED_INSTANT)));

        BaselineRiskCalculationRequest context = unmapped.assembleForCurrentUser(userId);

        assertFalse(context.weatherRisk().available());
        assertNull(context.weatherRisk().normalizedRisk());
        assertTrue(context.weatherRisk().unavailabilityReason().contains("outside the documented mapping"),
                context.weatherRisk().unavailabilityReason());
    }

    @Test
    void dropsTheWeatherContributionEntirelyWhenTheProviderIsUnreachable() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();
        var offline = assemblerObserving(WeatherObservationResult.unavailable("provider offline"));
        var fusion = new BaselineRiskFusionService(approvedProperties(), identityService,
                riskScoreRepository, new ObjectMapper());

        BaselineRiskResult result = fusion.calculateBaselineRisk(offline.assembleForCurrentUser(userId));

        // Back to historical + time-of-day only; no default or last-known weather is substituted.
        assertEquals(0, result.score().compareTo(new BigDecimal("27.5694227370")));
        assertEquals(RiskLevel.LOW, result.riskLevel());
        var weather = result.contributingFactors().stream()
                .filter(factor -> factor.factor() == RiskFactorType.WEATHER).findFirst().orElseThrow();
        assertFalse(weather.available());
        assertEquals(0, weather.contribution().signum());
    }

    @Test
    void assemblesTheTimeOfDayFeatureFromTheVerifiedMorthTableWithoutTouchingAnyRepository() {
        storedLocationIs(LATITUDE, LONGITUDE);
        morthPerLakhRecordsAreAvailable();

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        assertTrue(context.timeOfDayRisk().available());
        assertEquals(0, context.timeOfDayRisk().normalizedRisk().compareTo(TIME_OF_DAY_NORMALIZED));
        assertNull(context.timeOfDayRisk().unavailabilityReason());
    }

    @Test
    void keepsTheTimeOfDayFactorAvailableEvenWhereNoStateUtResolves() {
        // Mid Bay of Bengal: the national time-of-day distribution does not depend on geography.
        storedLocationIs(new BigDecimal("15.0"), new BigDecimal("85.0"));

        BaselineRiskCalculationRequest context = assembler.assembleForCurrentUser(userId);

        assertFalse(context.historicalIncidentRisk().available());
        assertTrue(context.timeOfDayRisk().available());
        assertEquals(0, context.timeOfDayRisk().normalizedRisk().compareTo(TIME_OF_DAY_NORMALIZED));
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

    /**
     * Stands in for the Open-Meteo transport only. The WMO-code mapping, the MoRTH severity table, and
     * the normalization all stay real, so no test here needs the Internet to reach a third party.
     */
    private record FixedWeatherObservationProvider(WeatherObservationResult result)
            implements WeatherObservationProvider {

        @Override
        public WeatherObservationResult currentWeather(BigDecimal latitude, BigDecimal longitude) {
            return result;
        }

        @Override
        public String providerName() {
            return PROVIDER;
        }
    }
}
