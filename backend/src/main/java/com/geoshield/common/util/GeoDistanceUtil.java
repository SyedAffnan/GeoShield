package com.geoshield.common.util;

/**
 * Canonical geodetic distance calculation utility using the spherical Haversine formula.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Mean Earth radius: {@code 6,371,000.0} meters (6,371.0 km).</li>
 *   <li>Strict coordinate validation: latitude in [-90.0, 90.0], longitude in [-180.0, 180.0].</li>
 *   <li>No (0.0, 0.0) fallback or automatic coordinate substitution.</li>
 *   <li>Consistent radians conversion across platforms.</li>
 * </ul>
 */
public final class GeoDistanceUtil {

    public static final double EARTH_RADIUS_METERS = 6371000.0;
    public static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtil() {
        // utility class
    }

    /**
     * Calculates the spherical distance between two coordinates in meters.
     *
     * @param lat1 latitude of first point in degrees [-90.0, 90.0]
     * @param lon1 longitude of first point in degrees [-180.0, 180.0]
     * @param lat2 latitude of second point in degrees [-90.0, 90.0]
     * @param lon2 longitude of second point in degrees [-180.0, 180.0]
     * @return distance in meters
     * @throws IllegalArgumentException if any coordinate is invalid, NaN, or infinite
     */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        validateCoordinates(lat1, lon1);
        validateCoordinates(lat2, lon2);

        if (Double.compare(lat1, lat2) == 0 && Double.compare(lon1, lon2) == 0) {
            return 0.0;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double sinDLat = Math.sin(dLat / 2.0);
        double sinDLon = Math.sin(dLon / 2.0);

        double a = sinDLat * sinDLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinDLon * sinDLon;

        // Clamp 'a' to [0.0, 1.0] to prevent floating point inaccuracies causing NaN in sqrt
        if (a < 0.0) {
            a = 0.0;
        } else if (a > 1.0) {
            a = 1.0;
        }

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculates the spherical distance between two coordinates in kilometers.
     *
     * @param lat1 latitude of first point in degrees [-90.0, 90.0]
     * @param lon1 longitude of first point in degrees [-180.0, 180.0]
     * @param lat2 latitude of second point in degrees [-90.0, 90.0]
     * @param lon2 longitude of second point in degrees [-180.0, 180.0]
     * @return distance in kilometers
     * @throws IllegalArgumentException if any coordinate is invalid, NaN, or infinite
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        return distanceMeters(lat1, lon1, lat2, lon2) / 1000.0;
    }

    /**
     * Validates that latitude and longitude are finite numbers within legal geographic boundaries.
     */
    public static void validateCoordinates(double lat, double lon) {
        if (Double.isNaN(lat) || Double.isInfinite(lat)) {
            throw new IllegalArgumentException("Latitude cannot be NaN or Infinite: " + lat);
        }
        if (Double.isNaN(lon) || Double.isInfinite(lon)) {
            throw new IllegalArgumentException("Longitude cannot be NaN or Infinite: " + lon);
        }
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude out of bounds [-90.0, 90.0]: " + lat);
        }
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude out of bounds [-180.0, 180.0]: " + lon);
        }
    }
}
