package com.geoshield.risk.dto;

import java.math.BigDecimal;

/** A score is accepted only when its producing source is explicitly available. */
public record RiskFactorInput(BigDecimal normalizedRisk, boolean available, String unavailabilityReason) {
    public RiskFactorInput {
        if (available && normalizedRisk == null) {
            throw new IllegalArgumentException("An available risk factor requires a normalized score");
        }
        if (!available && normalizedRisk != null) {
            throw new IllegalArgumentException("An unavailable risk factor must not contain a synthesized score");
        }
        if (available && unavailabilityReason != null) {
            throw new IllegalArgumentException("An available risk factor must not contain an unavailability reason");
        }
        if (!available && (unavailabilityReason == null || unavailabilityReason.isBlank())) {
            throw new IllegalArgumentException("An unavailable risk factor requires an explanation");
        }
        if (normalizedRisk != null && (normalizedRisk.signum() < 0
                || normalizedRisk.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("Normalized risk must be between 0 and 100");
        }
    }

    public RiskFactorInput(BigDecimal normalizedRisk, boolean available) {
        this(normalizedRisk, available, available ? null : "No approved source data is currently available.");
    }

    public static RiskFactorInput available(BigDecimal normalizedRisk) {
        return new RiskFactorInput(normalizedRisk, true, null);
    }

    public static RiskFactorInput unavailable() {
        return unavailable("No approved source data is currently available.");
    }

    public static RiskFactorInput unavailable(String reason) {
        return new RiskFactorInput(null, false, reason);
    }
}
