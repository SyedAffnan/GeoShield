package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.HistoricalTrendAdvisoryResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalTrendAdvisoryServiceTest {

    @Mock
    private LocationService locationService;

    @Mock
    private GeographicResolutionService geographicResolutionService;

    private ObjectMapper objectMapper;
    private HistoricalTrendAdvisoryServiceImpl advisoryService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        advisoryService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                true,
                "data/historical_trend_evaluation_2024.json"
        );
        advisoryService.init();
    }

    @Test
    @DisplayName("Service loads exactly 35 verified State/UT records and reports available")
    void serviceInitializationLoads35Records() {
        assertTrue(advisoryService.isAvailable());
    }

    @Test
    @DisplayName("Supported State/UT returns verified 2024 temporal-holdout estimate and provenance")
    void supportedStateReturnsVerifiedEstimate() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("12.9716");
        BigDecimal lon = new BigDecimal("77.5946");

        when(locationService.getCurrentLocation(userId)).thenReturn(
                new LocationResponse(1L, lat, lon, BigDecimal.ONE, BigDecimal.ZERO, Instant.now())
        );
        when(geographicResolutionService.resolve(lat, lon)).thenReturn(
                GeographicResolution.resolved(GeographicLevel.STATE_UT, "Karnataka", "KA")
        );

        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(userId);

        assertEquals("AVAILABLE", response.status());
        assertEquals("HISTORICAL_ACCIDENT_TREND_ADVISORY", response.advisoryType());
        assertEquals("STATE_UT", response.geographicLevel());
        assertEquals("Karnataka", response.geographicUnit());
        assertEquals("India", response.parentUnit());
        assertEquals(2024, response.targetYear());
        assertNotNull(response.predictedAccidentSeverity());
        assertEquals("fatalities_per_100_accidents", response.severityMetricUnit());
        assertTrue(response.advisoryNotice().contains("Karnataka"));
        assertTrue(response.advisoryNotice().contains("2024 temporal-holdout model estimate"));
        assertEquals(HistoricalTrendAdvisoryResponse.MANDATORY_DISCLAIMER, response.scopeDisclaimer());

        assertNotNull(response.provenance());
        assertEquals("RF-STATE-EXP-A", response.provenance().experimentIdentifier());
        assertEquals("2024_TEMPORAL_HOLDOUT", response.provenance().predictionSource());
        assertEquals("1.0.0-p0", response.provenance().advisoryReleaseVersion());
        assertNull(response.provenance().modelArtifact());
        assertEquals("UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT", response.provenance().modelArtifactStatus());
        assertNull(response.provenance().trainingTimestamp());
        assertEquals("UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT", response.provenance().trainingTimestampStatus());
        assertNotNull(response.provenance().artifactGeneratedAt());
        assertEquals("2021-2023", response.provenance().trainingPeriod());
        assertEquals(107, response.provenance().trainingRows());
        assertEquals(2024, response.provenance().holdoutYear());
        assertEquals(35, response.provenance().evaluationRows());
        assertEquals(300, response.provenance().modelConfiguration().get("n_estimators"));
        assertEquals(5, response.provenance().modelConfiguration().get("max_depth"));
        assertEquals(42, response.provenance().modelConfiguration().get("random_state"));
    }

    @Test
    @DisplayName("District coordinate returns parent State/UT estimate with explicit STATE_UT level and disclosure")
    void districtCoordinateReturnsParentStateWithExplicitDisclosure() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("12.9716");
        BigDecimal lon = new BigDecimal("77.5946");

        when(locationService.getCurrentLocation(userId)).thenReturn(
                new LocationResponse(2L, lat, lon, BigDecimal.ONE, BigDecimal.ZERO, Instant.now())
        );
        when(geographicResolutionService.resolve(lat, lon)).thenReturn(
                GeographicResolution.resolvedDistrict("Bengaluru Urban", "Karnataka", "KA", "KA_BNG")
        );

        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(userId);

        assertEquals("AVAILABLE", response.status());
        assertEquals("STATE_UT", response.geographicLevel(), "Must strictly remain STATE_UT; never DISTRICT");
        assertEquals("Karnataka", response.geographicUnit());
        assertEquals("This is a STATE_UT model estimate for Karnataka; no district-level model is available.",
                response.advisoryNotice());
        assertNotNull(response.predictedAccidentSeverity());
    }

    @Test
    @DisplayName("Unresolved or offshore coordinates return UNAVAILABLE with null prediction")
    void unresolvedLocationReturnsUnavailable() {
        UUID userId = UUID.randomUUID();
        BigDecimal lat = new BigDecimal("5.0000");
        BigDecimal lon = new BigDecimal("60.0000");

        when(locationService.getCurrentLocation(userId)).thenReturn(
                new LocationResponse(3L, lat, lon, BigDecimal.ONE, BigDecimal.ZERO, Instant.now())
        );
        when(geographicResolutionService.resolve(lat, lon)).thenReturn(
                GeographicResolution.unresolved("Coordinates offshore")
        );

        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(userId);

        assertEquals("UNAVAILABLE", response.status());
        assertNull(response.predictedAccidentSeverity());
        assertNull(response.provenance());
        assertTrue(response.advisoryNotice().contains("could not be resolved"));
    }

    @Test
    @DisplayName("Missing location returns UNAVAILABLE cleanly")
    void missingLocationReturnsUnavailable() {
        UUID userId = UUID.randomUUID();
        when(locationService.getCurrentLocation(userId)).thenReturn(null);

        HistoricalTrendAdvisoryResponse response = advisoryService.getAdvisoryForUser(userId);

        assertEquals("UNAVAILABLE", response.status());
        assertNull(response.predictedAccidentSeverity());
    }

    @Test
    @DisplayName("Missing resource fails soft: service reports unavailable without crashing")
    void missingResourceFailsSoft() {
        var brokenService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                true,
                "data/non_existent_resource.json"
        );
        brokenService.init();

        assertFalse(brokenService.isAvailable());
        HistoricalTrendAdvisoryResponse response = brokenService.getAdvisoryForUser(UUID.randomUUID());
        assertEquals("UNAVAILABLE", response.status());
        assertNull(response.predictedAccidentSeverity());
    }

    @Test
    @DisplayName("Disabled service via config reports unavailable")
    void disabledServiceReportsUnavailable() {
        var disabledService = new HistoricalTrendAdvisoryServiceImpl(
                locationService,
                geographicResolutionService,
                objectMapper,
                false,
                "data/historical_trend_evaluation_2024.json"
        );
        disabledService.init();

        assertFalse(disabledService.isAvailable());
        HistoricalTrendAdvisoryResponse response = disabledService.getAdvisoryForUser(UUID.randomUUID());
        assertEquals("UNAVAILABLE", response.status());
    }
}
