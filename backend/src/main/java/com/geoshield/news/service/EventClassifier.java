package com.geoshield.news.service;

import com.geoshield.news.dto.EventSeverity;
import com.geoshield.news.dto.SafetyEventCategory;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic classifier mapping raw article text to controlled safety categories
 * and conservative severity ratings based on factual event cues.
 *
 * <p>Explicitly rule-based and deterministic — not an opaque AI/NLP model.
 * Distinguishes the primary event subject from incidental emergency responders or agencies.
 */
@Component
public class EventClassifier {

    // Regex pattern identifying emergency response agencies and responder mentions.
    // These must NOT be mistaken for active fire or explosion event cues.
    private static final Pattern FIRE_AGENCY_PATTERN = Pattern.compile(
            "(?i)\\b(fire\\s+force|fire\\s+department|fire\\s+service(s)?|fire\\s+brigade|fire\\s+station|"
                    + "fire\\s+tender(s)?|fire\\s+engine(s)?|fire\\s+official(s)?|fire\\s+personnel|"
                    + "firefighter(s)?(\\s+assisted)?|firemen(\\s+assisted)?|fire\\s+rescue)\\b"
    );

    public SafetyEventCategory classifyCategory(String title, String description) {
        String rawCombined = (title + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);

        // 1. Natural Disaster check (widespread physical environment peril)
        if (containsAny(rawCombined, "landslide", "flood", "cyclone", "cloudburst", "earthquake", "inundation", "avalanche", "tsunami", "flash flood", "rockfall", "mudslide")) {
            return SafetyEventCategory.NATURAL_DISASTER;
        }

        // 2. Civil Disturbance check (curfew, riot, agitation)
        if (containsAny(rawCombined, "protest", "strike", "blockade", "curfew", "tear gas", "riot", "agitation", "bandh", "clash", "rasta roko", "chakka jam")) {
            return SafetyEventCategory.CIVIL_DISTURBANCE;
        }

        // 3. Crime & Violence check (active violent crime, murder, theft, kidnapping)
        // High priority: event subject must not be overshadowed by incidental responder assistance
        if (containsAny(rawCombined, "murder", "homicide", "body parts", "dismembered", "killed", "killing", "dead body",
                "corpse", "stabbed", "stabbing", "hacked", "assaulted", "assault", "robbery", "robbed", "theft", "thieves",
                "snatching", "snatched", "kidnap", "kidnapped", "kidnapping", "abducted", "abduction", "rape", "sexual assault",
                "missing person", "extortion", "gang", "gangster", "shot dead", "shooting", "gunfire", "burgled", "burglary",
                "crime branch", "murder investigation", "hacked to death", "stabbed to death")) {
            return SafetyEventCategory.CRIME_AND_VIOLENCE;
        }

        // 4. Fire & Explosion check:
        // Strip incidental responder/agency mentions before checking for fire event cues
        String withoutAgencies = FIRE_AGENCY_PATTERN.matcher(rawCombined).replaceAll(" ");
        if (containsAny(withoutAgencies, "blaze", "inferno", "explosion", "blast", "cylinder blast", "bomb blast",
                "fire broke out", "blaze broke out", "caught fire", "engulfed in fire", "engulfed in flames",
                "gutted by fire", "burned alive", "charred to death", "charred bodies", "burned in fire",
                "warehouse fire", "building fire", "factory fire", "house fire", "vehicle fire", "massive fire",
                "major fire", "flames", "fumes")
                || containsWord(withoutAgencies, "fire")) {
            return SafetyEventCategory.FIRE_AND_EXPLOSION;
        }

        // 5. Infrastructure Hazard check
        if (containsAny(rawCombined, "collapse", "gas leak", "electrocution", "flyover crack", "bridge collapse", "building collapse", "chemical leak", "toxic fumes")) {
            return SafetyEventCategory.INFRASTRUCTURE_HAZARD;
        }

        // 6. Traffic & Transit check
        if (containsAny(rawCombined, "accident", "crash", "collision", "overturn", "overturned", "derail", "derailment", "hit-and-run", "pile-up", "pileup", "bus fell", "train accident")) {
            return SafetyEventCategory.TRAFFIC_AND_TRANSIT;
        }

        return SafetyEventCategory.GENERAL_SAFETY;
    }

    public EventSeverity classifySeverity(String title, String description) {
        String combined = (title + " " + (description != null ? description : "")).toLowerCase(Locale.ROOT);

        // Critical cues: confirmed fatalities, murder, curfew, major evacuation
        if (containsAny(combined, "dead", "killed", "fatalities", "murder", "homicide", "body parts", "dismembered", "curfew declared", "curfew imposed", "evacuation ordered", "massive blast")) {
            return EventSeverity.CRITICAL;
        }
        // High cues: severe injury, road blockage, major fire, structural collapse, kidnapping
        if (containsAny(combined, "injured", "hospitalized", "critical condition", "landslide blocks", "highway blocked", "building collapse", "bridge collapse", "major fire", "blaze gut", "kidnapped", "shot dead", "stabbed")) {
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
        return text.matches(".*\\b" + Pattern.quote(word) + "\\b.*");
    }
}
