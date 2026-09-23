package com.geoshield.news.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable DTO representing a structured, location-relevant safety event derived from news reporting.
 */
public record RecentSafetyEventDto(
        UUID eventId,
        String eventGroupId,
        String title,
        String description,
        String sourceName,
        String sourceUrl,
        String imageUrl,
        Instant publishedAt,
        Instant retrievedAt,
        SafetyEventCategory category,
        EventSeverity severity,
        RelevanceTier relevance,
        String areaName,
        boolean isVerifiedSource,
        int relatedSourcesCount
) {
    public RecentSafetyEventDto withRelevance(RelevanceTier newRelevance) {
        return new RecentSafetyEventDto(
                eventId, eventGroupId, title, description, sourceName, sourceUrl,
                imageUrl, publishedAt, retrievedAt, category, severity,
                newRelevance, areaName, isVerifiedSource, relatedSourcesCount
        );
    }
}
