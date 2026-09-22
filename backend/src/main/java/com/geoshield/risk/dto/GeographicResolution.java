package com.geoshield.risk.dto;

import com.geoshield.historicaldata.entity.GeographicLevel;

public record GeographicResolution(
        GeographicLevel geographicLevel,
        String geographicUnit,
        String parentUnit,
        String stateCode,
        String districtCode,
        boolean resolved,
        String reason) {

    public GeographicResolution(GeographicLevel geographicLevel, String geographicUnit, String stateCode,
            boolean resolved, String reason) {
        this(geographicLevel, geographicUnit, "India", stateCode, null, resolved, reason);
    }

    public GeographicResolution(GeographicLevel geographicLevel, String geographicUnit, boolean resolved, String reason) {
        this(geographicLevel, geographicUnit, "India", null, null, resolved, reason);
    }

    public static GeographicResolution unresolved(String reason) {
        return new GeographicResolution(null, null, null, null, null, false, reason);
    }

    public static GeographicResolution resolved(GeographicLevel level, String unit) {
        return new GeographicResolution(level, unit, "India", null, null, true, null);
    }

    public static GeographicResolution resolved(GeographicLevel level, String unit, String stateCode) {
        return new GeographicResolution(level, unit, "India", stateCode, null, true, null);
    }

    public static GeographicResolution resolvedDistrict(String districtName, String parentState, String stateCode, String districtCode) {
        return new GeographicResolution(GeographicLevel.DISTRICT, districtName, parentState, stateCode, districtCode, true, null);
    }
}
