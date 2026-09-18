package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geoshield.emergencyservices.dto.EmergencyServiceCenterResponse;
import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmergencyServiceProximityRiskServiceTest {

    private EmergencyServicesService emergencyServicesService;
    private EmergencyServiceProximityRiskService riskService;

    private static final BigDecimal TEST_LAT = new BigDecimal("28.6139");
    private static final BigDecimal TEST_LON = new BigDecimal("77.2090");

    @BeforeEach
    void setUp() {
        emergencyServicesService = mock(EmergencyServicesService.class);
        riskService = new EmergencyServiceProximityRiskService(emergencyServicesService);
    }

    private EmergencyServiceCenterResponse createFacility(String name, CenterType type, double lat, double lon) {
        return new EmergencyServiceCenterResponse(
                1L, "osm-101", name, type,
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lon),
                "New Delhi", "Delhi", "+91 11 12345678");
    }

    @Test
    void returnsZeroRiskWhenFacilityIsAtExactTouristLocation() {
        var facility = createFacility("Central Hospital", CenterType.MEDICAL, 28.6139, 77.2090);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 0.0)));

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.SERVICE_PROXIMITY, feature.factor());
        assertEquals(BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP), feature.value());
        assertEquals(EmergencyServiceProximityRiskService.SOURCE, feature.source());
        assertEquals(EmergencyServiceProximityRiskService.NORMALIZATION, feature.normalization());
        assertTrue(feature.reason().contains("Central Hospital"));
        assertTrue(feature.reason().contains("0.00 km"));
    }

    @Test
    void scalesLinearlyAtIntermediateDistances() {
        var facility = createFacility("City Police Station", CenterType.RESPONSE_UNIT, 28.62, 77.21);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 5.0)));

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertTrue(feature.available());
        assertEquals(new BigDecimal("50.00000000"), feature.value());
        assertTrue(feature.reason().contains("City Police Station"));
        assertTrue(feature.reason().contains("5.00 km"));
    }

    @Test
    void scalesLinearlyAtOneKilometer() {
        var facility = createFacility("Local Fire Station", CenterType.FIRE, 28.62, 77.21);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 1.0)));

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertTrue(feature.available());
        assertEquals(new BigDecimal("10.00000000"), feature.value());
    }

    @Test
    void clampsToMaximumRiskAtTenKilometers() {
        var facility = createFacility("Suburban Clinic", CenterType.MEDICAL, 28.70, 77.30);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 10.0)));

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertTrue(feature.available());
        assertEquals(new BigDecimal("100.00000000"), feature.value());
        assertTrue(feature.reason().contains("exceeds 10.0 km threshold"));
    }

    @Test
    void clampsToMaximumRiskBeyondTenKilometers() {
        var facility = createFacility("Remote Station", CenterType.RESPONSE_UNIT, 29.00, 77.50);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 25.4)));

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertTrue(feature.available());
        assertEquals(new BigDecimal("100.00000000"), feature.value());
        assertTrue(feature.reason().contains("25.40 km"));
        assertTrue(feature.reason().contains("exceeds 10.0 km threshold; maximum risk applied"));
    }

    @Test
    void returnsUnavailableWhenCoordinatesAreNull() {
        NormalizedRiskFeature featureNullLat = riskService.proximityRisk(null, TEST_LON);
        assertFalse(featureNullLat.available());
        assertNull(featureNullLat.value());
        assertTrue(featureNullLat.reason().contains("No current location"));

        NormalizedRiskFeature featureNullLon = riskService.proximityRisk(TEST_LAT, null);
        assertFalse(featureNullLon.available());
        assertNull(featureNullLon.value());
    }

    @Test
    void returnsUnavailableWhenNoFacilitiesExist() {
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.empty());

        NormalizedRiskFeature feature = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertFalse(feature.available());
        assertNull(feature.value());
        assertTrue(feature.reason().contains("No emergency service centers available"));
    }

    @Test
    void isDeterministicAcrossRepeatedInvocations() {
        var facility = createFacility("City Hospital", CenterType.MEDICAL, 28.61, 77.20);
        when(emergencyServicesService.findNearestFacility(TEST_LAT.doubleValue(), TEST_LON.doubleValue()))
                .thenReturn(Optional.of(new NearestFacilityResult(facility, 3.456)));

        NormalizedRiskFeature f1 = riskService.proximityRisk(TEST_LAT, TEST_LON);
        NormalizedRiskFeature f2 = riskService.proximityRisk(TEST_LAT, TEST_LON);

        assertEquals(f1.value(), f2.value());
        assertEquals(f1.reason(), f2.reason());
        assertEquals(new BigDecimal("34.56000000"), f1.value());
    }
}
