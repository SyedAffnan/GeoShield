package com.geoshield.news.service;

import com.geoshield.news.dto.RecentSafetyEventDto;
import com.geoshield.news.dto.SafetyEventCategory;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Groups multiple articles reporting on the same physical incident across publishers
 * into a single canonical event with an updated relatedSourcesCount.
 *
 * <p>Employs canonical URL resolution, normalized title matching, description overlap,
 * salient entity/place recognition, and temporal proximity gating.
 */
@Component
public class EventDeduplicationService {

    private static final double TITLE_SIMILARITY_THRESHOLD = 0.38;
    private static final double COMPOSITE_SIMILARITY_THRESHOLD = 0.32;
    private static final Duration TEMPORAL_WINDOW = Duration.ofHours(24);

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "all", "also", "an", "and",
            "any", "are", "as", "at", "be", "because", "been", "before", "being",
            "below", "between", "both", "but", "by", "can", "could", "did", "do",
            "does", "doing", "down", "during", "each", "few", "for", "from", "further",
            "had", "has", "have", "having", "he", "her", "here", "hers", "herself",
            "him", "himself", "his", "how", "i", "if", "in", "into", "is", "it",
            "its", "itself", "just", "me", "more", "most", "my", "myself", "near",
            "no", "nor", "not", "now", "of", "off", "on", "once", "only", "or",
            "other", "our", "ours", "ourselves", "out", "over", "own", "s", "same",
            "she", "should", "so", "some", "such", "t", "than", "that", "the", "their",
            "theirs", "them", "themselves", "then", "there", "these", "they", "this",
            "those", "through", "to", "too", "under", "until", "up", "very", "was",
            "we", "were", "what", "when", "where", "which", "while", "who", "whom",
            "why", "will", "with", "would", "you", "your", "yours", "yourself", "yourselves",
            "says", "said", "reported", "reports", "today", "yesterday", "amid", "due"
    );

    private static final Pattern PUBLISHER_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s*[-|–—]\\s*(the hindu|the times of india|times of india|hindustan times|the indian express|indian express|ndtv|deccan herald|the tribune|tribune india|telegraph india|livemint|ani|pti|news18|india today|business standard|the wire|the print|firstpost|scroll|regional herald|state chronicle|weather sentinel).*$"
    );

    private static final Pattern GENERIC_PUBLISHER_SUFFIX = Pattern.compile(
            "\\s*[-|–—]\\s*[A-Z][a-zA-Z0-9\\s]{2,25}$"
    );

    private static final Pattern TEST_FIXTURE_PREFIX = Pattern.compile(
            "(?i)^\\[test fixture\\]\\s*"
    );

    // Salient entity cues: Highways (e.g. NH-44, NH 5), Expressways, multi-word named locations, institutions
    private static final Pattern HIGHWAY_PATTERN = Pattern.compile(
            "(?i)\\b(nh[-\\s]?\\d+|state highway[-\\s]?\\d+|outer ring road|bypass|expressway|flyover|bridge|junction)\\b"
    );

    // Incident action indicators for cross-source semantic alignment
    private static final Set<String> INCIDENT_ACTION_TERMS = Set.of(
            "protest", "strike", "curfew", "bandh", "agitation", "blockade", "clash",
            "landslide", "rockfall", "mudslide", "boulder",
            "flood", "waterlogging", "inundation", "cyclone", "cloudburst", "downpour",
            "accident", "crash", "collision", "overturn", "hit-and-run", "derail", "pileup",
            "fire", "blaze", "explosion", "blast", "inferno",
            "collapse", "leak", "electrocution",
            "robbery", "theft", "murder", "assault", "snatching", "killing"
    );

    private static final List<Set<String>> ACTION_FAMILIES = List.of(
            Set.of("landslide", "rockfall", "mudslide", "boulder", "collapse"),
            Set.of("flood", "waterlogging", "inundation", "cyclone", "cloudburst", "downpour"),
            Set.of("protest", "strike", "curfew", "bandh", "agitation", "blockade", "clash"),
            Set.of("accident", "crash", "collision", "overturn", "hit-and-run", "derail", "pileup"),
            Set.of("fire", "blaze", "explosion", "blast", "inferno"),
            Set.of("robbery", "theft", "murder", "assault", "snatching", "killing")
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
                    // Fold into existing cluster: update relatedSourcesCount
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
                String groupId = "grp-" + UUID.nameUUIDFromBytes((normalizeTitle(event.title()) + event.publishedAt().toString()).getBytes());
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

    /**
     * Evaluates whether two articles report on the same physical incident.
     */
    public boolean isSameIncident(RecentSafetyEventDto a, RecentSafetyEventDto b) {
        if (a == null || b == null) {
            return false;
        }

        // Rule 1: Canonical URL match (same article across syndications/query hits)
        String canonicalUrlA = canonicalizeUrl(a.sourceUrl());
        String canonicalUrlB = canonicalizeUrl(b.sourceUrl());
        if (!canonicalUrlA.isEmpty() && canonicalUrlA.equals(canonicalUrlB)) {
            return true;
        }

        // Temporal window check: must be within window (default 24h)
        if (a.publishedAt() != null && b.publishedAt() != null) {
            long hoursDiff = Math.abs(Duration.between(a.publishedAt(), b.publishedAt()).toHours());
            if (hoursDiff > TEMPORAL_WINDOW.toHours()) {
                return false;
            }
        }

        // Category compatibility: exact match OR one is GENERAL_SAFETY
        if (!isCategoryCompatible(a.category(), b.category())) {
            return false;
        }

        // Rule 2: Normalized Title similarity
        String normTitleA = normalizeTitle(a.title());
        String normTitleB = normalizeTitle(b.title());
        double titleSim = computeJaccardSimilarity(normTitleA, normTitleB);
        if (titleSim >= TITLE_SIMILARITY_THRESHOLD) {
            return true;
        }

        // Rule 3: Shared specific entities / infrastructure cues + shared action term
        Set<String> entitiesA = extractSalientEntities(a.title(), a.description());
        Set<String> entitiesB = extractSalientEntities(b.title(), b.description());
        Set<String> sharedEntities = new HashSet<>(entitiesA);
        sharedEntities.retainAll(entitiesB);

        // Filter out broad state/national names that are not specific landmarks
        sharedEntities.removeIf(e -> e.equalsIgnoreCase("india")
                || e.equalsIgnoreCase(a.areaName())
                || e.equalsIgnoreCase(b.areaName()));

        Set<String> actionsA = extractActionTerms(a.title(), a.description());
        Set<String> actionsB = extractActionTerms(b.title(), b.description());
        Set<String> sharedActions = new HashSet<>(actionsA);
        sharedActions.retainAll(actionsB);
        boolean actionsCompatible = !sharedActions.isEmpty() || shareActionFamily(actionsA, actionsB);

        // If they share a specific entity (like "foreshore estate" or "nh-44") AND compatible incident action
        if (!sharedEntities.isEmpty() && actionsCompatible) {
            return true;
        }

        // Rule 4: Composite text similarity (Title + Description)
        String compositeA = normTitleA + " " + (a.description() != null ? a.description().toLowerCase(Locale.ROOT) : "");
        String compositeB = normTitleB + " " + (b.description() != null ? b.description().toLowerCase(Locale.ROOT) : "");
        double compositeSim = computeJaccardSimilarity(compositeA, compositeB);
        if (compositeSim >= COMPOSITE_SIMILARITY_THRESHOLD && actionsCompatible) {
            return true;
        }

        return false;
    }

    private boolean shareActionFamily(Set<String> actionsA, Set<String> actionsB) {
        if (actionsA.isEmpty() || actionsB.isEmpty()) {
            return false;
        }
        for (Set<String> family : ACTION_FAMILIES) {
            boolean hasA = false;
            boolean hasB = false;
            for (String term : family) {
                if (actionsA.contains(term)) hasA = true;
                if (actionsB.contains(term)) hasB = true;
                if (hasA && hasB) return true;
            }
        }
        return false;
    }

    /**
     * Canonicalizes a URL by lowercasing host, removing trailing slashes,
     * stripping tracking query parameters (utm_*, ref, etc.), and removing fragments.
     */
    public String canonicalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String path = uri.getPath() != null ? uri.getPath().replaceAll("/+$", "") : "";

            // Strip tracking/session query parameters
            String query = uri.getQuery();
            String cleanQuery = "";
            if (query != null && !query.isBlank()) {
                List<String> preservedParams = new ArrayList<>();
                for (String param : query.split("&")) {
                    String lowerParam = param.toLowerCase(Locale.ROOT);
                    if (!lowerParam.startsWith("utm_")
                            && !lowerParam.startsWith("ref")
                            && !lowerParam.startsWith("source")
                            && !lowerParam.startsWith("fbclid")
                            && !lowerParam.startsWith("gclid")) {
                        preservedParams.add(param);
                    }
                }
                if (!preservedParams.isEmpty()) {
                    Collections.sort(preservedParams);
                    cleanQuery = "?" + String.join("&", preservedParams);
                }
            }

            return scheme + "://" + host + path + cleanQuery;
        } catch (Exception e) {
            return rawUrl.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        }
    }

    /**
     * Normalizes a headline by removing publisher tags, test fixture prefixes,
     * punctuation, and excessive whitespace.
     */
    public String normalizeTitle(String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank()) {
            return "";
        }
        String clean = TEST_FIXTURE_PREFIX.matcher(rawTitle.trim()).replaceFirst("");
        clean = PUBLISHER_SUFFIX_PATTERN.matcher(clean).replaceFirst("");
        clean = GENERIC_PUBLISHER_SUFFIX.matcher(clean).replaceFirst("");
        clean = clean.replaceAll("[^a-zA-Z0-9\\s]", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return clean;
    }

    private boolean isCategoryCompatible(SafetyEventCategory catA, SafetyEventCategory catB) {
        if (catA == catB) {
            return true;
        }
        if (catA == SafetyEventCategory.GENERAL_SAFETY || catB == SafetyEventCategory.GENERAL_SAFETY) {
            return true;
        }
        // Traffic hazards can overlap with infrastructure or general disaster
        if ((catA == SafetyEventCategory.TRAFFIC_AND_TRANSIT && catB == SafetyEventCategory.NATURAL_DISASTER)
                || (catA == SafetyEventCategory.NATURAL_DISASTER && catB == SafetyEventCategory.TRAFFIC_AND_TRANSIT)) {
            return true;
        }
        return false;
    }

    public double computeJaccardSimilarity(String textA, String textB) {
        Set<String> tokensA = tokenize(textA);
        Set<String> tokensB = tokenize(textB);

        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(tokensA);
        intersection.retainAll(tokensB);

        Set<String> union = new HashSet<>(tokensA);
        union.addAll(tokensB);

        return (double) intersection.size() / (double) union.size();
    }

    public Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] words = clean.split("\\s+");
        Set<String> tokens = new HashSet<>();
        for (String w : words) {
            if ((w.length() > 2 || w.matches("\\d+|nh|sh")) && !STOP_WORDS.contains(w)) {
                String stem = w;
                if (stem.endsWith("ing") && stem.length() > 5) {
                    stem = stem.substring(0, stem.length() - 3);
                } else if (stem.endsWith("ed") && stem.length() > 4) {
                    stem = stem.substring(0, stem.length() - 2);
                } else if (stem.endsWith("s") && !stem.endsWith("ss") && stem.length() > 3) {
                    stem = stem.substring(0, stem.length() - 1);
                }
                tokens.add(stem);
            }
        }
        return tokens;
    }

    private Set<String> extractSalientEntities(String title, String description) {
        String combined = (title != null ? title : "") + " " + (description != null ? description : "");
        Set<String> entities = new HashSet<>();

        // 1. Highways and named transit corridors
        Matcher hwMatcher = HIGHWAY_PATTERN.matcher(combined);
        while (hwMatcher.find()) {
            entities.add(hwMatcher.group().toLowerCase(Locale.ROOT).replaceAll("[-\\s]+", " "));
        }

        // 2. Multi-word title entities (e.g. "Foreshore Estate", "Secretariat project")
        // Find sequences of 2 or more capitalized words in original text
        Pattern capitalPhrasePattern = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)\\b");
        Matcher capMatcher = capitalPhrasePattern.matcher(combined);
        while (capMatcher.find()) {
            String phrase = capMatcher.group().toLowerCase(Locale.ROOT);
            if (!phrase.contains("the hindu") && !phrase.contains("times of india") && !phrase.contains("test fixture")) {
                entities.add(phrase);
            }
        }

        return entities;
    }

    private Set<String> extractActionTerms(String title, String description) {
        String combined = ((title != null ? title : "") + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);
        Set<String> actions = new HashSet<>();
        for (String term : INCIDENT_ACTION_TERMS) {
            if (combined.contains(term)) {
                actions.add(term);
            }
        }
        return actions;
    }
}

