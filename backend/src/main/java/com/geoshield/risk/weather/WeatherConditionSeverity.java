package com.geoshield.risk.weather;

import java.math.BigDecimal;

/**
 * One MoRTH Table 3.8 weather condition with its published 2024 counts and the two quantities
 * GeoShield derives from them.
 *
 * @param category                  the published weather condition
 * @param accidents                 published number of accidents in 2024 (exposure, not risk)
 * @param killed                    published persons killed in 2024
 * @param killedPerHundredAccidents {@code killed / accidents x 100}, the derived severity
 * @param normalizedRisk            {@code killedPerHundredAccidents} rescaled into [0,100]
 *                                  relative to the most severe published condition
 */
public record WeatherConditionSeverity(WeatherCategory category, long accidents, long killed,
        BigDecimal killedPerHundredAccidents, BigDecimal normalizedRisk) {

    /** The published label, for explanations that must quote the source wording. */
    public String label() {
        return category.publishedLabel();
    }
}
