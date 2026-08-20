package com.geoshield.risk.dto;

import java.math.BigDecimal;

/** A score is accepted only when its producing source is explicitly available. */
public record RiskFactorInput(BigDecimal normalizedRisk, boolean available) {
    public RiskFactorInput {
        if (available && normalizedRisk == null) {
            throw new IllegalArgumentException("An available risk factor requires a normalized score");
        }
        if (!available && normalizedRisk != null) {
            throw new IllegalArgumentException("An unavailable risk factor must not contain a synthesized score");
        }
        if (normalizedRisk != null && (normalizedRisk.signum() < 0
                || normalizedRisk.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("Normalized risk must be between 0 and 100");
        }
    }

    public static RiskFactorInput available(BigDecimal normalizedRisk) {
        return new RiskFactorInput(normalizedRisk, true);
    }

    public static RiskFactorInput unavailable() {
        return new RiskFactorInput(null, false);
    }
}
