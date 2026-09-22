package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Structured per-factor transparency and provenance detail for risk calculation factors.
 */
public record RiskFactorDetail(
        RiskFactorType factor,
        BigDecimal weight,
        boolean available,
        String rawValue,
        BigDecimal normalizedValue,
        BigDecimal weightedContribution,
        String reason,
        String explanation,
        String source,
        String sourceType,
        String sourceIdentifier,
        Instant observedAt,
        Long freshnessSeconds,
        String geographicScope,
        String normalizationDetails) {

    /** Backward-compatible constructor for existing tests and callers without provenance metadata. */
    public RiskFactorDetail(
            RiskFactorType factor,
            BigDecimal weight,
            boolean available,
            String rawValue,
            BigDecimal normalizedValue,
            BigDecimal weightedContribution,
            String reason,
            String explanation,
            String source) {
        this(factor, weight, available, rawValue, normalizedValue, weightedContribution, reason, explanation, source,
                null, null, null, null, null, null);
    }
}
