package com.geoshield.news.service;

import com.geoshield.news.dto.RelevanceTier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Evaluates the geographic relevance of an article to the user's target area.
 * Prevents national, state-wide, or cross-state articles from masquerading as local hyper-local incidents.
 *
 * <p>Enforces deterministic geographic conflict detection: a shared place-name token
 * (e.g. "Bhavani River") cannot pass as a local match when the article strongly identifies
 * a conflicting State/UT or district (e.g. Palakkad, Kerala).
 */
@Component
public class LocationRelevanceFilter {

    /**
     * All 36 Indian States and Union Territories (canonical Survey of India / MoRTH spellings).
     */
    public static final Set<String> ALL_INDIAN_STATES_UTS = Set.of(
            "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
            "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
            "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal"
    );

    private static final Map<String, String> STATE_ALIASES = Map.of(
            "tamilnadu", "Tamil Nadu",
            "orissa", "Odisha",
            "pondicherry", "Puducherry",
            "uttaranchal", "Uttarakhand",
            "kashmir", "Jammu and Kashmir"
    );

    private static final Pattern FEATURE_SUFFIX_PATTERN = Pattern.compile(
            "(?i)\\s+(river|lake|reservoir|dam|valley|expressway|canal|ghats|hills|delta|basin|nadi|puzha)\\b"
    );

    private static final Pattern DISTRICT_MENTION_PATTERN = Pattern.compile(
            "(?i)\\b([a-zA-Z\\s]{3,25})\\s+district\\b"
    );

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

        String cleanTitle = (title != null) ? title : "";
        String cleanDesc = (description != null) ? description : "";

        if (matchesWord(cleanTitle, targetArea)) {
            return RelevanceTier.HIGH;
        }

        int snippetBoundary = Math.min(cleanDesc.length(), 160);
        String snippet = cleanDesc.substring(0, snippetBoundary);
        if (matchesWord(snippet, targetArea)) {
            return RelevanceTier.MEDIUM;
        }

        if (matchesWord(cleanDesc, targetArea)) {
            return RelevanceTier.LOW;
        }

        return RelevanceTier.UNKNOWN;
    }

    /**
     * Hierarchical relevance evaluation considering locality, district, and state/UT context
     * with deterministic geographic conflict detection.
     *
     * @param locality    optional resolved locality/town
     * @param district    optional resolved district
     * @param stateUt     optional resolved State/UT
     * @param title       sanitized headline
     * @param description sanitized snippet
     * @return hierarchical relevance tier
     */
    public RelevanceTier calculateHierarchicalRelevance(
            String locality,
            String district,
            String stateUt,
            String title,
            String description) {

        String cleanTitle = (title != null) ? title : "";
        String cleanDesc = (description != null) ? description : "";
        String combined = cleanTitle + " " + cleanDesc;
        int snippetBoundary = Math.min(cleanDesc.length(), 160);
        String snippet = cleanDesc.substring(0, snippetBoundary);

        // Conflict check: if the article strongly identifies a different State/UT from tourist's stateUt
        boolean stateConflict = hasConflictingState(stateUt, combined);
        if (stateConflict) {
            // A conflicting State/UT strictly overrides shared locality/district tokens.
            return RelevanceTier.UNKNOWN;
        }

        // District conflict check: if article explicitly references another district
        boolean districtConflict = hasConflictingDistrict(district, combined);

        // 1. Locality matching (requires no district conflict, and non-feature or supported context)
        if (locality != null && !locality.isBlank() && !districtConflict) {
            boolean onlyFeature = isOnlyFeatureMention(combined, locality);
            boolean titleMatch = matchesWord(cleanTitle, locality);
            boolean snippetMatch = matchesWord(snippet, locality);
            boolean descMatch = matchesWord(cleanDesc, locality);

            if ((titleMatch || snippetMatch) && !onlyFeature) {
                return RelevanceTier.HIGH;
            }
            if (descMatch && !onlyFeature) {
                return RelevanceTier.MEDIUM;
            }
            // If only mentioned as a geographic feature (e.g. "Bhavani River"), require district or state support
            if (onlyFeature && (matchesWord(combined, district) || matchesWord(combined, stateUt))) {
                return RelevanceTier.MEDIUM;
            }
        }

        // 2. District matching
        if (district != null && !district.isBlank() && !districtConflict) {
            if (matchesWord(cleanTitle, district) || matchesWord(snippet, district)) {
                return RelevanceTier.MEDIUM;
            }
            if (matchesWord(cleanDesc, district)) {
                return RelevanceTier.LOW;
            }
        }

        // 3. State/UT matching (broad fallback)
        if (stateUt != null && !stateUt.isBlank()) {
            if (matchesWord(cleanTitle, stateUt) || matchesWord(snippet, stateUt) || matchesWord(cleanDesc, stateUt)) {
                return RelevanceTier.LOW;
            }
        }

        return RelevanceTier.UNKNOWN;
    }

    /**
     * Determines if the evaluated relevance tier satisfies the requirements of a specific retrieval level.
     *
     * @param level candidate retrieval level (LOCALITY, DISTRICT, STATE_UT, NATIONAL)
     * @param tier  calculated relevance tier
     * @return true if acceptable for that hierarchy level
     */
    public boolean isAcceptableForLevel(String level, RelevanceTier tier) {
        if (level == null || tier == null) {
            return false;
        }
        if ("NATIONAL".equalsIgnoreCase(level)) {
            return true;
        }
        if (tier == RelevanceTier.UNKNOWN) {
            return false;
        }
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "LOCALITY" -> tier == RelevanceTier.HIGH;
            case "DISTRICT" -> tier == RelevanceTier.HIGH || tier == RelevanceTier.MEDIUM;
            case "STATE_UT" -> tier == RelevanceTier.HIGH || tier == RelevanceTier.MEDIUM || tier == RelevanceTier.LOW;
            default -> isAcceptableRelevance(tier);
        };
    }

    /**
     * Checks if the text identifies a conflicting State/UT different from the user's resolved State/UT.
     */
    public boolean hasConflictingState(String touristState, String text) {
        if (touristState == null || touristState.isBlank() || text == null || text.isBlank()) {
            return false;
        }

        Set<String> mentionedStates = findMentionedStates(text);
        if (mentionedStates.isEmpty()) {
            return false;
        }

        String canonicalTouristState = canonicalizeState(touristState);

        // If another state is mentioned and tourist state is NOT mentioned: strong conflict
        boolean touristStateMentioned = mentionedStates.stream()
                .anyMatch(s -> s.equalsIgnoreCase(canonicalTouristState));

        Set<String> conflicting = new HashSet<>();
        for (String s : mentionedStates) {
            if (!s.equalsIgnoreCase(canonicalTouristState)) {
                conflicting.add(s);
            }
        }

        if (conflicting.isEmpty()) {
            return false;
        }

        // Conflicting states found. If tourist state is not mentioned, it's definitely a conflict
        if (!touristStateMentioned) {
            return true;
        }

        // Both tourist state and conflicting state mentioned.
        // Check if conflicting state is prominently featured in title
        return false;
    }

    /**
     * Checks if text explicitly references a different district (e.g. "Palakkad district" vs "Erode").
     */
    public boolean hasConflictingDistrict(String touristDistrict, String text) {
        if (touristDistrict == null || touristDistrict.isBlank() || text == null || text.isBlank()) {
            return false;
        }
        Matcher matcher = DISTRICT_MENTION_PATTERN.matcher(text);
        while (matcher.find()) {
            String mentionedDistrict = matcher.group(1).trim();
            if (isStateNameOrGeneric(mentionedDistrict)) {
                continue;
            }
            if (!matchesWord(mentionedDistrict, touristDistrict)
                    && !touristDistrict.equalsIgnoreCase(mentionedDistrict)) {
                return true;
            }
        }
        return false;
    }

    private boolean isStateNameOrGeneric(String word) {
        if (word == null || word.isBlank()) return true;
        String w = word.trim().toLowerCase(Locale.ROOT);
        if (Set.of("the", "this", "each", "every", "our", "all", "several", "neighboring", "neighbouring", "adjacent", "entire", "whole", "northern", "southern", "eastern", "western").contains(w)) {
            return true;
        }
        for (String state : ALL_INDIAN_STATES_UTS) {
            if (state.equalsIgnoreCase(w) || matchesWord(w, state) || matchesWord(state, w)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detects if locality appears exclusively as part of a natural feature name (e.g. "Bhavani River").
     */
    public boolean isOnlyFeatureMention(String text, String locality) {
        if (text == null || locality == null || locality.isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile(
                "(?i)\\b" + Pattern.quote(locality.trim()) + FEATURE_SUFFIX_PATTERN.pattern()
        );
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return false;
        }
        // If feature was found, strip all feature occurrences and check if locality is still present
        String stripped = matcher.replaceAll(" ");
        return !matchesWord(stripped, locality);
    }

    /**
     * Identifies all Indian States and UTs mentioned in text.
     */
    public Set<String> findMentionedStates(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> found = new HashSet<>();
        for (String state : ALL_INDIAN_STATES_UTS) {
            if (matchesWord(text, state)) {
                found.add(state);
            }
        }
        for (Map.Entry<String, String> alias : STATE_ALIASES.entrySet()) {
            if (matchesWord(text, alias.getKey())) {
                found.add(alias.getValue());
            }
        }
        return found;
    }

    private String canonicalizeState(String state) {
        if (state == null) return "";
        String trimmed = state.trim().toLowerCase(Locale.ROOT);
        return STATE_ALIASES.getOrDefault(trimmed, state.trim());
    }

    /**
     * @return true if relevance is sufficient for tourist awareness presentation (HIGH or MEDIUM)
     */
    public boolean isAcceptableRelevance(RelevanceTier tier) {
        return tier == RelevanceTier.HIGH || tier == RelevanceTier.MEDIUM;
    }

    /**
     * Case-insensitive whole-word match to avoid spurious substring matches.
     */
    public boolean matchesWord(String text, String phrase) {
        if (text == null || phrase == null || phrase.isBlank()) {
            return false;
        }
        String pattern = "(?i).*\\b" + Pattern.quote(phrase.trim()) + "\\b.*";
        return text.matches(pattern);
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

