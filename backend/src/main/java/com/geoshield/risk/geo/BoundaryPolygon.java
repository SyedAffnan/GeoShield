package com.geoshield.risk.geo;

/**
 * One polygon of a State/UT boundary: an outer ring plus zero or more hole rings,
 * with vertices in WGS84 degrees ordered {@code [longitude, latitude]} per RFC 7946.
 *
 * <p>Containment is decided by ray casting (the even-odd rule) against the outer
 * ring, then rejected if the point also falls inside any hole. The axis-aligned
 * extent is precomputed only as a fast reject: a point outside the extent
 * provably cannot be inside the ring, so it never changes the outcome and never
 * decides a positive match. Every positive result is decided by ray casting.
 */
final class BoundaryPolygon {
    private final double[][] outerRing;
    private final double[][][] holeRings;
    private final double minLongitude;
    private final double maxLongitude;
    private final double minLatitude;
    private final double maxLatitude;

    private BoundaryPolygon(double[][] outerRing, double[][][] holeRings) {
        this.outerRing = outerRing;
        this.holeRings = holeRings;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        for (double[] vertex : outerRing) {
            minLon = Math.min(minLon, vertex[0]);
            maxLon = Math.max(maxLon, vertex[0]);
            minLat = Math.min(minLat, vertex[1]);
            maxLat = Math.max(maxLat, vertex[1]);
        }
        this.minLongitude = minLon;
        this.maxLongitude = maxLon;
        this.minLatitude = minLat;
        this.maxLatitude = maxLat;
    }

    /**
     * Builds a polygon from GeoJSON polygon coordinates, where index 0 is the
     * outer ring and any further entries are holes.
     */
    static BoundaryPolygon of(double[][][] rings) {
        if (rings == null || rings.length == 0 || rings[0] == null || rings[0].length < 4) {
            throw new IllegalArgumentException("A boundary polygon needs a closed outer ring of at least 4 positions.");
        }
        double[][][] holes = new double[rings.length - 1][][];
        System.arraycopy(rings, 1, holes, 0, rings.length - 1);
        return new BoundaryPolygon(rings[0], holes);
    }

    boolean contains(double longitude, double latitude) {
        if (longitude < minLongitude || longitude > maxLongitude
                || latitude < minLatitude || latitude > maxLatitude) {
            return false;
        }
        if (!rayCastInside(longitude, latitude, outerRing)) {
            return false;
        }
        for (double[][] hole : holeRings) {
            if (rayCastInside(longitude, latitude, hole)) {
                return false;
            }
        }
        return true;
    }

    /** Even-odd ray casting against a single closed linear ring. */
    private static boolean rayCastInside(double longitude, double latitude, double[][] ring) {
        boolean inside = false;
        for (int current = 0, previous = ring.length - 1; current < ring.length; previous = current, current++) {
            double currentLongitude = ring[current][0];
            double currentLatitude = ring[current][1];
            double previousLongitude = ring[previous][0];
            double previousLatitude = ring[previous][1];
            boolean straddlesLatitude = (currentLatitude > latitude) != (previousLatitude > latitude);
            if (!straddlesLatitude) {
                continue;
            }
            double intersectionLongitude = (previousLongitude - currentLongitude)
                    * (latitude - currentLatitude) / (previousLatitude - currentLatitude) + currentLongitude;
            if (longitude < intersectionLongitude) {
                inside = !inside;
            }
        }
        return inside;
    }

    int vertexCount() {
        int total = outerRing.length;
        for (double[][] hole : holeRings) {
            total += hole.length;
        }
        return total;
    }
}
