package com.geoshield.news.service;

import com.geoshield.news.dto.RecentSafetyEventDto;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Groups multiple articles reporting on the same physical incident to avoid
 * duplicate threat perception on the tourist dashboard.
 */
@Component
public class EventDeduplicationService {

    private static final double SIMILARITY_THRESHOLD = 0.45;
    private static final Duration TEMPORAL_WINDOW = Duration.ofHours(12);

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "in", "on", "at", "to", "for", "of", "and", "or",
            "is", "was", "are", "were", "near", "from", "after", "by", "with", "over"
    );

    /**
     * Deduplicates a list of events by clustering similar articles within the same temporal window.
     * Returns a list where duplicates are folded into a single canonical event with an updated relatedSourcesCount.
     */
    public List<RecentSafetyEventDto> deduplicate(List<RecentSafetyEventDto> rawEvents) {
        if (rawEvents == null || rawEvents.isEmpty()) {
            return List.of();
        }

        List<RecentSafetyEventDto> consolidated = new ArrayList<>();

        for (RecentSafetyEventDto event : rawEvents) {
            boolean merged = false;

            for (int i = 0; i < consolidated.size(); i++) {
                RecentSafetyEventDto existing = consolidated.get(i);

                if (isSameIncident(existing, event)) {
                    // Fold into existing cluster
                    int updatedRelatedCount = existing.relatedSourcesCount() + 1;
                    RecentSafetyEventDto updated = new RecentSafetyEventDto(
                            existing.eventId(),
                            existing.eventGroupId(),
                            existing.title(),
                            existing.description(),
                            existing.sourceName(),
                            existing.sourceUrl(),
                            existing.imageUrl(),
                            existing.publishedAt(),
                            existing.retrievedAt(),
                            existing.category(),
                            existing.severity(),
                            existing.relevance(),
                            existing.areaName(),
                            existing.isVerifiedSource(),
                            updatedRelatedCount
                    );
                    consolidated.set(i, updated);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                // New distinct event cluster
                String groupId = "grp-" + UUID.nameUUIDFromBytes((event.title() + event.publishedAt().toString()).getBytes());
                RecentSafetyEventDto canonical = new RecentSafetyEventDto(
                        event.eventId(),
                        groupId,
                        event.title(),
                        event.description(),
                        event.sourceName(),
                        event.sourceUrl(),
                        event.imageUrl(),
                        event.publishedAt(),
                        event.retrievedAt(),
                        event.category(),
                        event.severity(),
                        event.relevance(),
                        event.areaName(),
                        event.isVerifiedSource(),
                        1
                );
                consolidated.add(canonical);
            }
        }

        return consolidated;
    }

    private boolean isSameIncident(RecentSafetyEventDto a, RecentSafetyEventDto b) {
        // Must be in the same category
        if (a.category() != b.category()) {
            return false;
        }

        // Must be within temporal window
        long hoursDiff = Math.abs(Duration.between(a.publishedAt(), b.publishedAt()).toHours());
        if (hoursDiff > TEMPORAL_WINDOW.toHours()) {
            return false;
        }

        // Check title Jaccard word similarity
        double similarity = computeTitleSimilarity(a.title(), b.title());
        return similarity >= SIMILARITY_THRESHOLD;
    }

    private double computeTitleSimilarity(String titleA, String titleB) {
        Set<String> tokensA = tokenize(titleA);
        Set<String> tokensB = tokenize(titleB);

        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / (double) union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null) {
            return Set.of();
        }
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] words = clean.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String w : words) {
            if (w.length() > 2 && !STOP_WORDS.contains(w)) {
                tokens.add(w);
            }
        }
        return tokens;
    }
}
