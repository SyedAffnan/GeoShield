package com.geoshield.emergencyservices.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.entity.EmergencyServiceCenter;
import com.geoshield.emergencyservices.repository.EmergencyServiceCenterRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmergencyServicesServiceImplTest {

    private EmergencyServiceCenterRepository repository;
    private EmergencyServicesServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(EmergencyServiceCenterRepository.class);
        service = new EmergencyServicesServiceImpl(repository);
    }

    @Test
    void loadsBundledCsvCorrectlyWithExpectedCounts() {
        List<EmergencyServiceCenter> centers = service.loadCentersFromCsv();

        assertEquals(57939, centers.size(), "Should load exactly 57,939 validated OSM-mapped emergency-service records from nationwide OSM dataset");

        long medicalCount = centers.stream().filter(c -> c.getCenterType() == CenterType.MEDICAL).count();
        long policeCount = centers.stream().filter(c -> c.getCenterType() == CenterType.RESPONSE_UNIT).count();
        long fireCount = centers.stream().filter(c -> c.getCenterType() == CenterType.FIRE).count();

        assertEquals(53633, medicalCount, "Should contain 53,633 MEDICAL facilities");
        assertEquals(3784, policeCount, "Should contain 3,784 RESPONSE_UNIT facilities");
        assertEquals(522, fireCount, "Should contain 522 FIRE facilities");

        // Verify all coordinates reside within India territory
        for (EmergencyServiceCenter center : centers) {
            assertNotNull(center.getName());
            assertFalse(center.getName().isBlank());
            assertNotNull(center.getSourceId());
            assertTrue(center.getSourceId().startsWith("osm-"));

            double lat = center.getLatitude().doubleValue();
            double lon = center.getLongitude().doubleValue();
            assertTrue(lat >= 6.0 && lat <= 38.0, "Latitude " + lat + " must be within India bounds");
            assertTrue(lon >= 68.0 && lon <= 98.0, "Longitude " + lon + " must be within India bounds");
        }
    }

    @Test
    void findsNearestFacilityCorrectlyFromLoadedDataset() {
        List<EmergencyServiceCenter> centers = service.loadCentersFromCsv();
        when(repository.findAll()).thenReturn(centers);

        // Near New Delhi Central (Connaught Place: 28.6315, 77.2167)
        Optional<NearestFacilityResult> nearest = service.findNearestFacility(28.6315, 77.2167);

        assertTrue(nearest.isPresent());
        assertNotNull(nearest.get().facility());
        // Distance should be well within 5 km in central New Delhi
        assertTrue(nearest.get().distanceKm() < 5.0, "Nearest facility should be within 5 km");
        assertTrue(nearest.get().distanceKm() >= 0.0);
    }

    @Test
    void findsNearestFacilityByTypeCorrectly() {
        List<EmergencyServiceCenter> centers = service.loadCentersFromCsv();
        when(repository.findAll()).thenReturn(centers);

        // Connaught Place coordinates
        double lat = 28.6315;
        double lon = 77.2167;

        Optional<NearestFacilityResult> med = service.findNearestFacilityByType(lat, lon, CenterType.MEDICAL);
        Optional<NearestFacilityResult> police = service.findNearestFacilityByType(lat, lon, CenterType.RESPONSE_UNIT);
        Optional<NearestFacilityResult> fire = service.findNearestFacilityByType(lat, lon, CenterType.FIRE);

        assertTrue(med.isPresent());
        assertEquals(CenterType.MEDICAL, med.get().facility().centerType());

        assertTrue(police.isPresent());
        assertEquals(CenterType.RESPONSE_UNIT, police.get().facility().centerType());

        assertTrue(fire.isPresent());
        assertEquals(CenterType.FIRE, fire.get().facility().centerType());
    }

    @Test
    void haversineDistanceBetweenKnownPointsIsAccurate() {
        // New Delhi (28.6139, 77.2090) to Jaipur (26.9124, 75.7873) is ~238 km
        double distance = EmergencyServicesServiceImpl.haversineDistanceKm(28.6139, 77.2090, 26.9124, 75.7873);
        assertTrue(distance >= 235.0 && distance <= 242.0, "Distance should be ~238 km, was " + distance);

        // Same point distance is 0.0
        assertEquals(0.0, EmergencyServicesServiceImpl.haversineDistanceKm(28.6139, 77.2090, 28.6139, 77.2090), 1e-6);
    }

    @Test
    void returnsEmptyWhenNoFacilitiesLoaded() {
        when(repository.findAll()).thenReturn(List.of());

        Optional<NearestFacilityResult> result = service.findNearestFacility(28.6139, 77.2090);
        assertTrue(result.isEmpty());
    }
}
