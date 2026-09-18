package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentRiskFeatureServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-08-24T12:00:00Z");
    // MG Road, Bengaluru
    private static final BigDecimal TOURIST_LAT = new BigDecimal("12.9716");
    private static final BigDecimal TOURIST_LON = new BigDecimal("77.5946");

    private IncidentRiskFeatureService service;

    @BeforeEach
    void setUp() {
        service = new IncidentRiskFeatureService(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
    }

    @Test
    void noIncidentsReturnsZeroScoreAndIsAvailable() {
        NormalizedRiskFeature feature = service.userReportRisk(List.of(), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.USER_REPORT, feature.factor());
        assertEquals(0, feature.value().compareTo(BigDecimal.ZERO));
        assertTrue(feature.reason().contains("No active user-reported incidents found"));
    }

    @Test
    void nullIncidentListReturnsZeroScoreAndIsAvailable() {
        NormalizedRiskFeature feature = service.userReportRisk(null, TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertEquals(0, feature.value().compareTo(BigDecimal.ZERO));
    }

    @Test
    void missingTouristCoordinatesReturnsUnavailableWithoutFabrication() {
        NormalizedRiskFeature feature = service.userReportRisk(List.of(createIncident("Theft", TOURIST_LAT, TOURIST_LON, 0, "REPORTED")), null, null);

        assertFalse(feature.available());
        assertNull(feature.value());
        assertTrue(feature.reason().contains("No current location is available"));
    }

    @Test
    void oneRecentNearbyHazardProducesExactDecayedScore() {
        // Road hazard at distance 0, 0 hours old -> base 25, spatial 1.0, temporal 1.0, status 1.0 = 25.0
        IncidentResponse incident = createIncident("Road hazard", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        NormalizedRiskFeature feature = service.userReportRisk(List.of(incident), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertEquals(0, feature.value().compareTo(new BigDecimal("25.00000000")));
    }

    @Test
    void oneRecentNearbyTheftProducesExactSpatialAndTemporalDecay() {
        // Coordinates ~2.0 km north: lat shift ~ 0.018 degrees
        BigDecimal incidentLat = TOURIST_LAT.add(new BigDecimal("0.0180"));
        // Reported 6 hours ago: temporal factor = 1 - 6/24 = 0.75
        // Distance ~ 1.996 km -> spatial factor ~ 1 - 1.996/10 = 0.8004
        // Theft base severity = 75.0
        // Expected ~ 75 * 0.8004 * 0.75 * 1.0 ~ 45.02
        IncidentResponse incident = createIncident("Theft", incidentLat, TOURIST_LON, 6, "REPORTED");
        NormalizedRiskFeature feature = service.userReportRisk(List.of(incident), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertTrue(feature.value().compareTo(new BigDecimal("40.0")) > 0);
        assertTrue(feature.value().compareTo(new BigDecimal("50.0")) < 0);
    }

    @Test
    void multipleNearbyIncidentsAccumulateRisk() {
        IncidentResponse hazard = createIncident("Hazard", TOURIST_LAT, TOURIST_LON, 0, "REPORTED"); // 25
        IncidentResponse theft = createIncident("Theft", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");   // 75

        NormalizedRiskFeature feature = service.userReportRisk(List.of(hazard, theft), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        // 25 + 75 = 100
        assertEquals(0, feature.value().compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void oldIncidentBeyond24HoursProducesZeroRisk() {
        // Reported 26 hours ago
        IncidentResponse oldIncident = createIncident("Assault", TOURIST_LAT, TOURIST_LON, 26, "REPORTED");

        NormalizedRiskFeature feature = service.userReportRisk(List.of(oldIncident), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertEquals(0, feature.value().compareTo(BigDecimal.ZERO));
    }

    @Test
    void incidentOutside10KmRadiusProducesZeroRisk() {
        // ~25 km away (lat difference ~ 0.23 degrees)
        BigDecimal farLat = TOURIST_LAT.add(new BigDecimal("0.2300"));
        IncidentResponse farIncident = createIncident("Robbery", farLat, TOURIST_LON, 0, "REPORTED");

        NormalizedRiskFeature feature = service.userReportRisk(List.of(farIncident), TOURIST_LAT, TOURIST_LON);

        assertTrue(feature.available());
        assertEquals(0, feature.value().compareTo(BigDecimal.ZERO));
    }

    @Test
    void differentIncidentSeveritiesProduceDifferentiatedScores() {
        IncidentResponse assault = createIncident("Armed Assault", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse theft = createIncident("Theft of phone", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse accident = createIncident("Traffic Accident", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse hazard = createIncident("Road hazard", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");

        BigDecimal assaultScore = service.userReportRisk(List.of(assault), TOURIST_LAT, TOURIST_LON).value();
        BigDecimal theftScore = service.userReportRisk(List.of(theft), TOURIST_LAT, TOURIST_LON).value();
        BigDecimal accidentScore = service.userReportRisk(List.of(accident), TOURIST_LAT, TOURIST_LON).value();
        BigDecimal hazardScore = service.userReportRisk(List.of(hazard), TOURIST_LAT, TOURIST_LON).value();

        assertEquals(0, assaultScore.compareTo(new BigDecimal("100.00000000")));
        assertEquals(0, theftScore.compareTo(new BigDecimal("75.00000000")));
        assertEquals(0, accidentScore.compareTo(new BigDecimal("50.00000000")));
        assertEquals(0, hazardScore.compareTo(new BigDecimal("25.00000000")));

        assertTrue(assaultScore.compareTo(theftScore) > 0);
        assertTrue(theftScore.compareTo(accidentScore) > 0);
        assertTrue(accidentScore.compareTo(hazardScore) > 0);
    }

    @Test
    void incidentStatusWeightsApplyCorrectly() {
        IncidentResponse reported = createIncident("Robbery", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse responding = createIncident("Robbery", TOURIST_LAT, TOURIST_LON, 0, "RESPONDING");
        IncidentResponse resolved = createIncident("Robbery", TOURIST_LAT, TOURIST_LON, 0, "RESOLVED");
        IncidentResponse cancelled = createIncident("Robbery", TOURIST_LAT, TOURIST_LON, 0, "CANCELLED");

        assertEquals(0, service.userReportRisk(List.of(reported), TOURIST_LAT, TOURIST_LON).value().compareTo(new BigDecimal("100.00000000")));
        assertEquals(0, service.userReportRisk(List.of(responding), TOURIST_LAT, TOURIST_LON).value().compareTo(new BigDecimal("80.00000000")));
        assertEquals(0, service.userReportRisk(List.of(resolved), TOURIST_LAT, TOURIST_LON).value().compareTo(BigDecimal.ZERO));
        assertEquals(0, service.userReportRisk(List.of(cancelled), TOURIST_LAT, TOURIST_LON).value().compareTo(BigDecimal.ZERO));
    }

    @Test
    void clampsScoreAtOneHundredWhenMultipleSevereIncidentsOccur() {
        IncidentResponse assault1 = createIncident("Assault", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse assault2 = createIncident("Assault", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");
        IncidentResponse assault3 = createIncident("Assault", TOURIST_LAT, TOURIST_LON, 0, "REPORTED");

        NormalizedRiskFeature feature = service.userReportRisk(List.of(assault1, assault2, assault3), TOURIST_LAT, TOURIST_LON);

        assertEquals(0, feature.value().compareTo(new BigDecimal("100.00000000")));
    }

    @Test
    void deterministicRepeatedCalculationProducesIdenticalResult() {
        IncidentResponse incident = createIncident("Theft", TOURIST_LAT, TOURIST_LON, 2, "REPORTED");

        NormalizedRiskFeature run1 = service.userReportRisk(List.of(incident), TOURIST_LAT, TOURIST_LON);
        NormalizedRiskFeature run2 = service.userReportRisk(List.of(incident), TOURIST_LAT, TOURIST_LON);

        assertEquals(run1.value(), run2.value());
        assertEquals(run1.reason(), run2.reason());
        assertEquals(run1.source(), run2.source());
        assertEquals(run1.normalization(), run2.normalization());
    }

    private IncidentResponse createIncident(String type, BigDecimal lat, BigDecimal lon, int hoursAgo, String status) {
        Instant reportedAt = FIXED_NOW.minusSeconds(hoursAgo * 3600L);
        return new IncidentResponse(UUID.randomUUID(), type, "Description for " + type, lat, lon, status,
                "a".repeat(64), IncidentSourceType.USER_REPORTED, reportedAt);
    }
}
