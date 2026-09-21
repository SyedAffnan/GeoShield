package com.geoshield.news.service;

import com.geoshield.news.dto.RelevanceTier;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Evaluates the geographic relevance of an article to the user's target area.
 * Prevents national or state-wide articles from masquerading as local hyper-local incidents.
 */
@Component
public class LocationRelevanceFilter {

    /**
     * Determines whether an article matches the target area name and returns the relevance tier.
     *
     * @param targetArea the resolved area (e.g., "Shimla", "Bengaluru", "Kerala")
     * @param title      sanitized headline
     * @param description sanitized snippet
     * @return relevance tier
     */
    public RelevanceTier calculateRelevance(String targetArea, String title, String description) {
        if (targetArea == null || targetArea.isBlank()) {
            return RelevanceTier.UNKNOWN;
        }

        String area = targetArea.trim().toLowerCase(Locale.ROOT);
        String cleanTitle = (title != null) ? title.toLowerCase(Locale.ROOT) : "";
        String cleanDesc = (description != null) ? description.toLowerCase(Locale.ROOT) : "";

        // High: Area explicitly mentioned in the headline
        if (cleanTitle.contains(area)) {
            return RelevanceTier.HIGH;
        }

        // Medium: Area mentioned in the opening sentence/snippet of the description
        int snippetBoundary = Math.min(cleanDesc.length(), 140);
        if (cleanDesc.substring(0, snippetBoundary).contains(area)) {
            return RelevanceTier.MEDIUM;
        }

        // Low: Area mentioned later in the body
        if (cleanDesc.contains(area)) {
            return RelevanceTier.LOW;
        }

        return RelevanceTier.UNKNOWN;
    }

    /**
     * @return true if relevance is sufficient for tourist awareness presentation (HIGH or MEDIUM)
     */
    public boolean isAcceptableRelevance(RelevanceTier tier) {
        return tier == RelevanceTier.HIGH || tier == RelevanceTier.MEDIUM;
    }

    /**
     * Sanitizes external text by stripping HTML tags and excess whitespace.
     */
    public String sanitizeText(String raw) {
        if (raw == null) {
            return "";
        }
        // Strip HTML tags and entities
        String stripped = raw.replaceAll("<[^>]*>", "")
                .replaceAll("&[a-zA-Z0-9#]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return stripped;
    }
}
