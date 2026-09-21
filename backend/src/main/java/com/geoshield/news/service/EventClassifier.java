package com.geoshield.news.service;

import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.SafetyEventCategory;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Deterministic classifier mapping raw article text to controlled safety categories
 * and conservative severity ratings based on factual cues.
 *
 * <p>Explicitly rule-based and deterministic — not an opaque AI/NLP model.
 */
@Component
public class EventClassifier {

    public SafetyEventCategory classifyCategory(String title, String description) {
        String combined = (title + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);

        if (containsAny(combined, "landslide", "flood", "cyclone", "cloudburst", "earthquake", "inundation", "avalanche", "tsunami")) {
            return SafetyEventCategory.NATURAL_DISASTER;
        }
        if (containsAny(combined, "protest", "strike", "blockade", "curfew", "tear gas", "riot", "agitation", "bandh", "clash")) {
            return SafetyEventCategory.CIVIL_DISTURBANCE;
        }
        if (containsAny(combined, "blaze", "explosion", "cylinder blast", "inferno", "fumes") || containsWord(combined, "fire")) {
            return SafetyEventCategory.FIRE_AND_EXPLOSION;
        }
        if (containsAny(combined, "collapse", "gas leak", "electrocution", "flyover crack", "bridge collapse")) {
            return SafetyEventCategory.INFRASTRUCTURE_HAZARD;
        }
        if (containsAny(combined, "accident", "crash", "collision", "overturn", "derail", "hit-and-run", "pile-up", "bus fell", "train")) {
            return SafetyEventCategory.TRAFFIC_AND_TRANSIT;
        }
        if (containsAny(combined, "robbery", "theft", "assault", "murder", "snatching", "stabbed", "shot dead", "burgled", "kidnap", "gang", "extortion")) {
            return SafetyEventCategory.CRIME_AND_VIOLENCE;
        }
        return SafetyEventCategory.GENERAL_SAFETY;
    }

    public EventSeverity classifySeverity(String title, String description) {
        String combined = (title + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);

        // Critical cues: confirmed fatalities, curfew, major evacuation
        if (containsAny(combined, "dead", "killed", "fatalities", "curfew declared", "curfew imposed", "evacuation ordered", "massive blast")) {
            return EventSeverity.CRITICAL;
        }
        // High cues: severe injury, road blockage, major fire, structural collapse
        if (containsAny(combined, "injured", "hospitalized", "critical condition", "landslide blocks", "highway blocked", "building collapse", "bridge collapse", "major fire", "blaze gut")) {
            return EventSeverity.HIGH;
        }
        // Moderate cues: robbery, theft, waterlogging, peaceful rally, minor collision
        if (containsAny(combined, "robbed", "theft", "snatched", "waterlogging", "jam", "protest", "strike", "traffic diverted", "arrested")) {
            return EventSeverity.MODERATE;
        }
        // Low cues: advisory, warning, mock drill, inspection
        if (containsAny(combined, "advisory", "caution", "alert issued", "drill", "patrol", "survey")) {
            return EventSeverity.LOW;
        }
        return EventSeverity.UNKNOWN;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsWord(String text, String word) {
        return text.matches(".*\\b" + word + "\\b.*");
    }
}
