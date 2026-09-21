package com.geoshield.risk.geo;

import java.util.List;

/**
 * One State/UT boundary as a MultiPolygon: the point is inside the State/UT if it
 * is inside any constituent polygon. This is what makes discontiguous units such
 * as Andaman and Nicobar Islands, Lakshadweep, and Puducherry resolve correctly.
 *
 * <p>{@code stateName} is already normalized to the exact MoRTH
 * {@code geographicUnit} spelling by the offline preprocessing step, so no
 * name translation happens at runtime.
 */
final class StateBoundary {
    private final String stateName;
    private final String stateCode;
    private final List<BoundaryPolygon> polygons;

    StateBoundary(String stateName, String stateCode, List<BoundaryPolygon> polygons) {
        this.stateName = stateName;
        this.stateCode = stateCode;
        this.polygons = List.copyOf(polygons);
    }

    boolean contains(double longitude, double latitude) {
        for (BoundaryPolygon polygon : polygons) {
            if (polygon.contains(longitude, latitude)) {
                return true;
            }
        }
        return false;
    }

    public String stateName() {
        return stateName;
    }

    public String stateCode() {
        return stateCode;
    }

    int vertexCount() {
        int total = 0;
        for (BoundaryPolygon polygon : polygons) {
            total += polygon.vertexCount();
        }
        return total;
    }
}
