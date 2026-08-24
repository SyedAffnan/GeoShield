package com.geoshield.risk.service;

import com.geoshield.risk.dto.GeographicResolution;
import java.math.BigDecimal;

public interface GeographicResolutionService {
    /**
     * Resolves a WGS84 coordinate to the geographic unit the historical data supports.
     *
     * <p>Implementations must never guess a unit and must return an explicit unresolved
     * result instead of throwing when the coordinate cannot be resolved.
     *
     * @param latitude  WGS84 latitude in degrees
     * @param longitude WGS84 longitude in degrees
     */
    GeographicResolution resolve(BigDecimal latitude, BigDecimal longitude);

    // State/UT resolution is served offline by BoundaryGeographicResolutionService using a
    // bundled Survey of India / NWIC derived boundary resource. City-level granularity
    // remains open: no verified offline city boundary dataset is configured.
}
