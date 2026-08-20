package com.geoshield.risk.dto;

import com.geoshield.historicaldata.entity.GeographicLevel;

public record GeographicResolution(GeographicLevel geographicLevel, String geographicUnit, boolean resolved, String reason) {
    public static GeographicResolution unresolved(String reason) { return new GeographicResolution(null, null, false, reason); }
    public static GeographicResolution resolved(GeographicLevel level, String unit) { return new GeographicResolution(level, unit, true, null); }
}
