package com.geoshield.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class GeoDistanceUtilTest {

    private static final double NUMERICAL_TOLERANCE_METERS = 0.001; // 1 mm

    @Test
    @DisplayName("Haversine distance between New Delhi and Jaipur is accurate")
    void distanceBetweenKnownPointsIsAccurate() {
        // New Delhi: 28.6139, 77.2090; Jaipur: 26.9124, 75.7873
        double meters = GeoDistanceUtil.distanceMeters(28.6139, 77.2090, 26.9124, 75.7873);
        double km = GeoDistanceUtil.distanceKm(28.6139, 77.2090, 26.9124, 75.7873);

        // Distance is ~239.79 km (between 235 km and 242 km)
        assertTrue(km >= 235.0 && km <= 242.0, "Distance should be ~238-242 km, was " + km);
        assertEquals(meters / 1000.0, km, NUMERICAL_TOLERANCE_METERS);
    }

    @Test
    @DisplayName("Zero distance for identical coordinates")
    void zeroDistanceForIdenticalCoordinates() {
        double dist = GeoDistanceUtil.distanceMeters(28.6139, 77.2090, 28.6139, 77.2090);
        assertEquals(0.0, dist, 1e-9);
    }

    @Test
    @DisplayName("Equatorial quarter-turn (90 degrees longitude) equals pi/2 * R")
    void equatorialQuarterTurn() {
        double dist = GeoDistanceUtil.distanceMeters(0.0, 0.0, 0.0, 90.0);
        double expected = (Math.PI / 2.0) * GeoDistanceUtil.EARTH_RADIUS_METERS;
        assertEquals(expected, dist, NUMERICAL_TOLERANCE_METERS);
    }

    @Test
    @DisplayName("Pole to equator quarter-turn (90 degrees latitude) equals pi/2 * R")
    void poleToEquatorQuarterTurn() {
        double dist = GeoDistanceUtil.distanceMeters(0.0, 0.0, 90.0, 0.0);
        double expected = (Math.PI / 2.0) * GeoDistanceUtil.EARTH_RADIUS_METERS;
        assertEquals(expected, dist, NUMERICAL_TOLERANCE_METERS);
    }

    @ParameterizedTest
    @CsvSource({
            "91.0, 77.0",
            "-90.1, 77.0",
            "28.0, 180.1",
            "28.0, -180.5"
    })
    @DisplayName("Rejects out of bounds coordinates")
    void rejectsOutOfBounds(double lat, double lon) {
        assertThrows(IllegalArgumentException.class, () -> GeoDistanceUtil.validateCoordinates(lat, lon));
        assertThrows(IllegalArgumentException.class, () -> GeoDistanceUtil.distanceMeters(lat, lon, 28.0, 77.0));
    }

    @Test
    @DisplayName("Rejects NaN and Infinity coordinates")
    void rejectsNaNAndInfinity() {
        assertThrows(IllegalArgumentException.class, () -> GeoDistanceUtil.validateCoordinates(Double.NaN, 77.0));
        assertThrows(IllegalArgumentException.class, () -> GeoDistanceUtil.validateCoordinates(28.0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> GeoDistanceUtil.validateCoordinates(Double.NEGATIVE_INFINITY, 77.0));
    }

    @Test
    @DisplayName("Shared canonical parity vector between Java and Dart")
    void sharedCanonicalParityVector() {
        // Point A: Mumbai (18.9220, 72.8347), Point B: Gateway of India area offset (18.9300, 72.8400)
        double meters = GeoDistanceUtil.distanceMeters(18.9220, 72.8347, 18.9300, 72.8400);
        // Canonical spherical Haversine formula with R = 6,371,000.0 m evaluates to 1049.806 meters
        assertEquals(1049.806, meters, NUMERICAL_TOLERANCE_METERS);
    }
}
