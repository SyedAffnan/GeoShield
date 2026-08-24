package com.geoshield.risk.weather;

import java.util.Arrays;
import java.util.Optional;

/**
 * The five weather conditions MoRTH publishes in Table 3.8, and the only categories for which a
 * published accident severity exists.
 *
 * <p>No category may be added here without a corresponding published MoRTH row. Inventing a
 * sixth category would mean inventing the accident statistics behind it.
 */
public enum WeatherCategory {
    SUNNY_CLEAR("Sunny / clear"),
    RAINY("Rainy"),
    FOGGY_MISTY("Foggy & misty"),
    HAIL_SLEET("Hail / sleet"),

    /**
     * MoRTH's unclassified residual bucket.
     *
     * <p>Published, so it participates in the normalization denominator, but intentionally
     * unreachable from {@link WmoWeatherCodeMapper}: MoRTH does not say what conditions this
     * bucket contains, so no observed weather code can be justifiably assigned to it.
     */
    OTHERS("Others");

    private final String publishedLabel;

    WeatherCategory(String publishedLabel) {
        this.publishedLabel = publishedLabel;
    }

    /** The label exactly as printed in Table 3.8, used to match the bundled resource rows. */
    public String publishedLabel() {
        return publishedLabel;
    }

    /** Resolves a Table 3.8 row label, so the resource is matched on published text, not order. */
    public static Optional<WeatherCategory> fromPublishedLabel(String label) {
        return Arrays.stream(values())
                .filter(category -> category.publishedLabel.equalsIgnoreCase(label.trim()))
                .findFirst();
    }
}
