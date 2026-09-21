package com.geoshield.news.dto;

import java.math.BigDecimal;

/**
 * Value object specifying parameters for a location-based safety news lookup.
 */
public record NewsQuery(
        BigDecimal latitude,
        BigDecimal longitude,
        String locality,
        String district,
        String resolvedArea,
        String resolutionLevel,
        SafetyEventCategory category,
        int limit
) {
    public NewsQuery {
        if (limit <= 0) {
            limit = 10;
        }
    }
}
