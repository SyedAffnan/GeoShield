package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geoshield.emergencyservices.dto.EmergencyServiceCenterResponse;
import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import com.geoshield.risk.weather.MorthWeatherSeverityTable;
import com.geoshield.risk.weather.WeatherObservation;
import com.geoshield.risk.weather.WeatherObservationProvider;
import com.geoshield.risk.weather.WeatherObservationResult;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RiskFactorProvenanceTest {

    private static final BigDecimal LAT = new BigDecimal("12.9716");
    private static final BigDecimal LON = new BigDecimal("77.5946");
    private static final Instant FIXED_NOW = Instant.parse("2026-08-24T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("Historical factor provenance: static dataset reference, null observedAt, and state scope")
    void historicalFactorProvenance() {
        HistoricalDataService mockHistorical = mock(HistoricalDataService.class);
        when(mockHistorical.getHistoricalSafetyRecords(GeographicLevel.STATE_UT)).thenReturn(List.of(
                new HistoricalSafetyRecordSummary(
                        HistoricalRiskFeatureService.MORTH_SOURCE,
                        2024,
                        GeographicLevel.STATE_UT,
                        "Karnataka",
                        "State / UT - wise Total Number of Persons Injured in Road Accidents",
                        "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024",
                        new BigDecimal("77.2"),
                        false
                ),
                new HistoricalSafetyRecordSummary(
                        HistoricalRiskFeatureService.MORTH_SOURCE,
                        2024,
                        GeographicLevel.STATE_UT,
                        "Kerala",
                        "State / UT - wise Total Number of Persons Injured in Road Accidents",
                        "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024",
                        new BigDecimal("154.4"),
                        false
                )
        ));

        HistoricalRiskFeatureService service = new HistoricalRiskFeatureService(mockHistorical);
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Karnataka", "29");

        NormalizedRiskFeature feature = service.historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals("HISTORICAL", feature.sourceType());
        assertEquals("MoRTH-2024-STATE-UT", feature.sourceIdentifier());
        assertEquals("STATE_UT: Karnataka", feature.geographicScope());
        assertNull(feature.observedAt(), "Historical statistical publications must have null observedAt");
        assertNull(feature.freshnessSeconds(), "Historical statistical publications must have null freshnessSeconds");
        assertNotNull(feature.normalizationDetails());
        assertTrue(feature.normalizationDetails().contains("metricValue"));
    }

    @Test
    @DisplayName("Historical factor unavailable: produces explicit missing provenance and reason code")
    void historicalFactorUnavailableProvenance() {
        HistoricalDataService mockHistorical = mock(HistoricalDataService.class);
        HistoricalRiskFeatureService service = new HistoricalRiskFeatureService(mockHistorical);
        GeographicResolution unresolvable = GeographicResolution.unresolved("Outside boundary");

        NormalizedRiskFeature feature = service.historicalIncidentRisk(unresolvable);

        assertFalse(feature.available());
        assertEquals("HISTORICAL", feature.sourceType());
        assertNull(feature.sourceIdentifier());
        assertEquals("COORDINATES_UNRESOLVED", feature.geographicScope());
        assertEquals("HISTORICAL_DATA_UNAVAILABLE", feature.reasonCode());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
    }

    @Test
    @DisplayName("Weather factor provenance: provider observedAt, freshness calculation, and grid cell scope")
    void weatherFactorProvenance() {
        Instant weatherTime = FIXED_NOW.minus(Duration.ofMinutes(15));
        WeatherObservationProvider mockProvider = mock(WeatherObservationProvider.class);
        when(mockProvider.currentWeather(LAT, LON)).thenReturn(
                WeatherObservationResult.observed(new WeatherObservation("Open-Meteo", 61, weatherTime))
        );

        WeatherRiskService service = new WeatherRiskService(mockProvider, new MorthWeatherSeverityTable(), FIXED_CLOCK);
        NormalizedRiskFeature feature = service.currentRisk(LAT, LON);

        assertTrue(feature.available());
        assertEquals("EXTERNAL_API", feature.sourceType());
        assertEquals("Open-Meteo-WMO-61", feature.sourceIdentifier());
        assertEquals(weatherTime, feature.observedAt());
        assertEquals(900L, feature.freshnessSeconds());
        assertEquals("LOCATION_GRID_0.01DEG[12.97,77.59]", feature.geographicScope());
        assertNotNull(feature.normalizationDetails());
    }

    @Test
    @DisplayName("Weather factor clock drift: future timestamp clamped with drift indicator")
    void weatherFactorClockDriftProvenance() {
        Instant futureWeather = FIXED_NOW.plus(Duration.ofMinutes(5));
        WeatherObservationProvider mockProvider = mock(WeatherObservationProvider.class);
        when(mockProvider.currentWeather(LAT, LON)).thenReturn(
                WeatherObservationResult.observed(new WeatherObservation("Open-Meteo", 0, futureWeather))
        );

        WeatherRiskService service = new WeatherRiskService(mockProvider, new MorthWeatherSeverityTable(), FIXED_CLOCK);
        NormalizedRiskFeature feature = service.currentRisk(LAT, LON);

        assertTrue(feature.available());
        assertEquals(0L, feature.freshnessSeconds());
        assertTrue(feature.normalizationDetails().contains("[CLOCK_DRIFT_DETECTED]"));
    }

    @Test
    @DisplayName("Weather factor minor clock skew <=60s: clamped to 0s without CLOCK_DRIFT_DETECTED flag")
    void weatherFactorMinorClockSkewProvenance() {
        Instant nearFutureWeather = FIXED_NOW.plus(Duration.ofSeconds(30));
        WeatherObservationProvider mockProvider = mock(WeatherObservationProvider.class);
        when(mockProvider.currentWeather(LAT, LON)).thenReturn(
                WeatherObservationResult.observed(new WeatherObservation("Open-Meteo", 0, nearFutureWeather))
        );

        WeatherRiskService service = new WeatherRiskService(mockProvider, new MorthWeatherSeverityTable(), FIXED_CLOCK);
        NormalizedRiskFeature feature = service.currentRisk(LAT, LON);

        assertTrue(feature.available());
        assertEquals(0L, feature.freshnessSeconds(), "Freshness must clamp to 0 when timestamp is slightly in future");
        assertFalse(feature.normalizationDetails().contains("CLOCK_DRIFT_DETECTED"),
                "Minor clock skew <= 60s must not trigger CLOCK_DRIFT_DETECTED flag");
    }

    @Test
    @DisplayName("Time-of-day factor provenance: static temporal rule, null observedAt, and national scope")
    void timeOfDayFactorProvenance() {
        TimeOfDayRiskService service = new TimeOfDayRiskService(FIXED_CLOCK, new MorthTimeOfDayDistribution());
        NormalizedRiskFeature feature = service.currentRisk();

        assertTrue(feature.available());
        assertEquals("TEMPORAL_RULE", feature.sourceType());
        assertNotNull(feature.sourceIdentifier());
        assertTrue(feature.sourceIdentifier().startsWith("MoRTH-2024-Table-7.3-"));
        assertEquals("NATIONAL", feature.geographicScope());
        assertNull(feature.observedAt(), "Static MoRTH time-band distribution must have null observedAt");
        assertNull(feature.freshnessSeconds(), "Static MoRTH time-band distribution must have null freshnessSeconds");
    }

    @Test
    @DisplayName("Emergency facility proximity: actual sourceId precedence, null observedAt, and bounded radius scope")
    void facilityProximityActualSourceIdPrecedence() {
        EmergencyServicesService mockService = mock(EmergencyServicesService.class);
        EmergencyServiceCenterResponse facility = new EmergencyServiceCenterResponse(
                42L,
                "osm-node-987654",
                "City General Hospital",
                CenterType.MEDICAL,
                LAT,
                LON,
                "Bengaluru",
                "Karnataka",
                "108"
        );
        when(mockService.findNearestFacility(anyDouble(), anyDouble())).thenReturn(
                Optional.of(new NearestFacilityResult(facility, 2.50))
        );

        EmergencyServiceProximityRiskService service = new EmergencyServiceProximityRiskService(mockService);
        NormalizedRiskFeature feature = service.proximityRisk(LAT, LON);

        assertTrue(feature.available());
        assertEquals("FACILITY_REGISTRY", feature.sourceType());
        assertEquals("osm-node-987654", feature.sourceIdentifier());
        assertEquals("RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]", feature.geographicScope());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
    }

    @Test
    @DisplayName("Emergency facility proximity: fallback to ESC-id when sourceId is blank")
    void facilityProximityFallbackSourceId() {
        EmergencyServicesService mockService = mock(EmergencyServicesService.class);
        EmergencyServiceCenterResponse facility = new EmergencyServiceCenterResponse(
                88L,
                null, // No sourceId
                "Central Police Station",
                CenterType.RESPONSE_UNIT,
                LAT,
                LON,
                "Bengaluru",
                "Karnataka",
                "100"
        );
        when(mockService.findNearestFacility(anyDouble(), anyDouble())).thenReturn(
                Optional.of(new NearestFacilityResult(facility, 1.20))
        );

        EmergencyServiceProximityRiskService service = new EmergencyServiceProximityRiskService(mockService);
        NormalizedRiskFeature feature = service.proximityRisk(LAT, LON);

        assertTrue(feature.available());
        assertEquals("ESC-88", feature.sourceIdentifier());
    }

    @Test
    @DisplayName("Incident factor provenance: 0 active incidents produces matched=0 query footprint")
    void incidentFactorZeroIncidentsProvenance() {
        IncidentRiskFeatureService service = new IncidentRiskFeatureService(FIXED_CLOCK);
        NormalizedRiskFeature feature = service.userReportRisk(List.of(), LAT, LON);

        assertTrue(feature.available());
        assertEquals("USER_INCIDENTS", feature.sourceType());
        assertEquals("QUERY(radius=10.0km,window=24.0h,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=0)", feature.sourceIdentifier());
        assertEquals("RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]", feature.geographicScope());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
    }

    @Test
    @DisplayName("Incident factor provenance: multiple incidents bounded to 5 UUIDs and newest reportedAt")
    void incidentFactorMultipleIncidentsBoundedProvenance() {
        IncidentRiskFeatureService service = new IncidentRiskFeatureService(FIXED_CLOCK);

        List<IncidentResponse> incidents = new ArrayList<>();
        Instant baseTime = FIXED_NOW.minus(Duration.ofHours(2));
        for (int i = 1; i <= 7; i++) {
            incidents.add(new IncidentResponse(
                    UUID.fromString("00000000-0000-0000-0000-00000000000" + i),
                    "Theft",
                    "Incident " + i,
                    LAT,
                    LON,
                    "REPORTED",
                    "hash" + i,
                    IncidentSourceType.USER_REPORTED,
                    baseTime.plus(Duration.ofMinutes(i * 10)) // newest will be i=7
            ));
        }

        NormalizedRiskFeature feature = service.userReportRisk(incidents, LAT, LON);

        assertTrue(feature.available());
        assertEquals("USER_INCIDENTS", feature.sourceType());
        // Newest incident is i=7
        Instant expectedNewest = baseTime.plus(Duration.ofMinutes(70));
        assertEquals(expectedNewest, feature.observedAt());
        assertEquals(Duration.between(expectedNewest, FIXED_NOW).getSeconds(), feature.freshnessSeconds());

        // Footprint must contain first 5 UUIDs + "...+2_more"
        String sourceId = feature.sourceIdentifier();
        assertNotNull(sourceId);
        assertTrue(sourceId.contains("matched=7"));
        assertTrue(sourceId.contains("00000000-0000-0000-0000-000000000007"));
        assertTrue(sourceId.contains("...+2_more"));
    }

    @Test
    @DisplayName("Geographic provenance defensive validation: invalid inputs return COORDINATES_UNAVAILABLE")
    void defensiveGeographicValidationHandlesInvalids() {
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(null, null));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(new BigDecimal("12.97"), null));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(null, new BigDecimal("77.59")));

        // Double primitives / boundary validation
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(Double.NaN, 77.59));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(12.97, Double.NaN));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(Double.POSITIVE_INFINITY, 77.59));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(Double.NEGATIVE_INFINITY, 77.59));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(12.97, Double.POSITIVE_INFINITY));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(12.97, Double.NEGATIVE_INFINITY));

        // Latitude outside [-90, 90]
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(90.001, 77.59));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(-90.001, 77.59));

        // Longitude outside [-180, 180]
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(12.97, 180.001));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toLocationGrid(12.97, -180.001));

        // Radius overload invalid checks
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toRadiusAroundLocationGrid(null, null, 10.0));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toRadiusAroundLocationGrid(12.97, 77.59, -1.0));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toRadiusAroundLocationGrid(12.97, 77.59, Double.NaN));
        assertEquals("COORDINATES_UNAVAILABLE", GeographicProvenanceUtil.toRadiusAroundLocationGrid(12.97, 77.59, Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Geographic provenance deterministic bucketing: positive, negative, and exact boundaries")
    void geographicProvenanceDeterministicBucketingAndBoundaries() {
        // Positive coordinates (Bengaluru)
        assertEquals("LOCATION_GRID_0.01DEG[12.97,77.59]",
                GeographicProvenanceUtil.toLocationGrid(new BigDecimal("12.9716"), new BigDecimal("77.5946")));
        assertEquals("RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]",
                GeographicProvenanceUtil.toRadiusAroundLocationGrid(new BigDecimal("12.9716"), new BigDecimal("77.5946"), 10.0));

        // Negative coordinates (Sydney)
        assertEquals("LOCATION_GRID_0.01DEG[-33.87,151.20]",
                GeographicProvenanceUtil.toLocationGrid(new BigDecimal("-33.8688"), new BigDecimal("151.2093")));

        // Exact boundaries
        assertEquals("LOCATION_GRID_0.01DEG[90.00,180.00]",
                GeographicProvenanceUtil.toLocationGrid(90.0, 180.0));
        assertEquals("LOCATION_GRID_0.01DEG[-90.00,-180.00]",
                GeographicProvenanceUtil.toLocationGrid(-90.0, -180.0));

        // Origin
        assertEquals("LOCATION_GRID_0.01DEG[0.00,0.00]",
                GeographicProvenanceUtil.toLocationGrid(0.0, 0.0));
    }
}
