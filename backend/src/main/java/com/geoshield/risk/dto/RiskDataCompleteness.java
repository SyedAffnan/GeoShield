package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Explicit completeness and data degradation metadata evaluated strictly across the 5 current live risk factors.
 * Dormant factors (connectivity and other context) are excluded.
 */
public record RiskDataCompleteness(
        int availableFactorCount,
        int expectedLiveFactorCount,
        BigDecimal availabilityRatio,
        boolean degraded,
        List<RiskFactorType> missingFactors,
        Map<RiskFactorType, String> missingReasons) {

    public static final int EXPECTED_LIVE_FACTORS = 5;

    public RiskDataCompleteness {
        if (missingFactors == null) {
            missingFactors = List.of();
        } else {
            missingFactors = Collections.unmodifiableList(missingFactors);
        }
        if (missingReasons == null) {
            missingReasons = Map.of();
        } else {
            missingReasons = Collections.unmodifiableMap(missingReasons);
        }
    }

    public static RiskDataCompleteness of(
            int availableFactorCount,
            List<RiskFactorType> missingFactors,
            Map<RiskFactorType, String> missingReasons) {
        BigDecimal ratio = BigDecimal.valueOf(availableFactorCount)
                .divide(BigDecimal.valueOf(EXPECTED_LIVE_FACTORS), 1, RoundingMode.HALF_UP);
        boolean degraded = availableFactorCount < EXPECTED_LIVE_FACTORS;
        return new RiskDataCompleteness(
                availableFactorCount,
                EXPECTED_LIVE_FACTORS,
                ratio,
                degraded,
                missingFactors,
                missingReasons);
    }
}
