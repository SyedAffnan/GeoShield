package com.geoshield.risk.service;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Utility for producing privacy-preserving, deterministic geographic provenance representations.
 *
 * <p>Uses a 0.01-degree grid bucketing (~1.11 km latitude, ~0.89–1.10 km longitude across India)
 * to represent the geographic scope of observations without leaking high-precision GPS coordinates
 * into audit records.
 *
 * <p>Performs defensive validation against nulls, NaNs, infinities, and out-of-range coordinates
 * (latitude [-90, 90], longitude [-180, 180]).
 */
public final class GeographicProvenanceUtil {
    public static final String COORDINATES_UNAVAILABLE = "COORDINATES_UNAVAILABLE";
    private static final double STEP_DEGREES = 0.01;

    private GeographicProvenanceUtil() { }

    public static String toLocationGrid(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return COORDINATES_UNAVAILABLE;
        }
        return toLocationGrid(latitude.doubleValue(), longitude.doubleValue());
    }

    public static String toLocationGrid(double latitude, double longitude) {
        if (isInvalidCoordinates(latitude, longitude)) {
            return COORDINATES_UNAVAILABLE;
        }
        double bucketLat = bucket(latitude);
        double bucketLon = bucket(longitude);
        return String.format(Locale.ROOT, "LOCATION_GRID_0.01DEG[%.2f,%.2f]", bucketLat, bucketLon);
    }

    public static String toRadiusAroundLocationGrid(BigDecimal latitude, BigDecimal longitude, double radiusKm) {
        if (latitude == null || longitude == null) {
            return COORDINATES_UNAVAILABLE;
        }
        return toRadiusAroundLocationGrid(latitude.doubleValue(), longitude.doubleValue(), radiusKm);
    }

    public static String toRadiusAroundLocationGrid(double latitude, double longitude, double radiusKm) {
        if (isInvalidCoordinates(latitude, longitude) || Double.isNaN(radiusKm) || Double.isInfinite(radiusKm) || radiusKm < 0) {
            return COORDINATES_UNAVAILABLE;
        }
        double bucketLat = bucket(latitude);
        double bucketLon = bucket(longitude);
        return String.format(Locale.ROOT, "RADIUS_%.0fKM_AROUND_LOCATION_GRID[%.2f,%.2f]", radiusKm, bucketLat, bucketLon);
    }

    public static boolean isInvalidCoordinates(double latitude, double longitude) {
        return Double.isNaN(latitude)
                || Double.isInfinite(latitude)
                || Double.isNaN(longitude)
                || Double.isInfinite(longitude)
                || latitude < -90.0
                || latitude > 90.0
                || longitude < -180.0
                || longitude > 180.0;
    }

    private static double bucket(double coordinate) {
        double val = Math.floor(coordinate / STEP_DEGREES) * STEP_DEGREES;
        if (val == -0.0 || Math.abs(val) < 1e-12) {
            return 0.0;
        }
        return val;
    }
}
