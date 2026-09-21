package com.geoshield.risk.dto;

import com.geoshield.historicaldata.entity.GeographicLevel;

public record GeographicResolution(
        GeographicLevel geographicLevel,
        String geographicUnit,
        String stateCode,
        boolean resolved,
        String reason) {

    public GeographicResolution(GeographicLevel geographicLevel, String geographicUnit, boolean resolved, String reason) {
        this(geographicLevel, geographicUnit, null, resolved, reason);
    }

    public static GeographicResolution unresolved(String reason) {
        return new GeographicResolution(null, null, null, false, reason);
    }

    public static GeographicResolution resolved(GeographicLevel level, String unit) {
        return new GeographicResolution(level, unit, null, true, null);
    }

    public static GeographicResolution resolved(GeographicLevel level, String unit, String stateCode) {
        return new GeographicResolution(level, unit, stateCode, true, null);
    }
}
