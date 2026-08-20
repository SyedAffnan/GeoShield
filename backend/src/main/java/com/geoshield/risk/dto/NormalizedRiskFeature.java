package com.geoshield.risk.dto;

import java.math.BigDecimal;

/** Provenance-preserving normalized feature contract for baseline and future AI consumers. */
public record NormalizedRiskFeature(RiskFactorType factor, BigDecimal value, boolean available, String source,
        String reason, String normalization) {
    public RiskFactorInput toRiskFactorInput() {
        return available ? RiskFactorInput.available(value) : RiskFactorInput.unavailable(reason);
    }
    public static NormalizedRiskFeature unavailable(RiskFactorType factor, String source, String reason, String normalization) {
        return new NormalizedRiskFeature(factor, null, false, source, reason, normalization);
    }
}
