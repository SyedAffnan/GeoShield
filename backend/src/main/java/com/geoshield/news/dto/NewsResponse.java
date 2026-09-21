package com.geoshield.news.dto;

import java.time.Instant;
import java.util.List;

/**
 * Top-level response envelope for location-based safety news intelligence.
 */
public record NewsResponse(
        String resolvedArea,
        String resolutionLevel,
        Instant retrievedAt,
        boolean cached,
        boolean providerAvailable,
        int eventsCount,
        List<RecentSafetyEventDto> events
) {
    public static NewsResponse empty(String resolvedArea, String resolutionLevel, boolean providerAvailable) {
        return new NewsResponse(
                resolvedArea,
                resolutionLevel,
                Instant.now(),
                false,
                providerAvailable,
                0,
                List.of()
        );
    }

    public NewsResponse withCached(boolean isCached) {
        return new NewsResponse(
                this.resolvedArea,
                this.resolutionLevel,
                this.retrievedAt,
                isCached,
                this.providerAvailable,
                this.eventsCount,
                this.events
        );
    }
}
