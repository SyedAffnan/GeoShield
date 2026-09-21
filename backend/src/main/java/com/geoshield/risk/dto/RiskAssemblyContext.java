package com.geoshield.risk.dto;

import com.geoshield.location.dto.LocationResponse;

/**
 * Context container holding both the assembled baseline risk calculation request
 * and the resolved location snapshot, preventing duplicate location lookups.
 */
public record RiskAssemblyContext(
        BaselineRiskCalculationRequest request,
        LocationResponse location,
        GeographicResolution resolution
) {
    public RiskAssemblyContext(BaselineRiskCalculationRequest request, LocationResponse location) {
        this(request, location, null);
    }
}
