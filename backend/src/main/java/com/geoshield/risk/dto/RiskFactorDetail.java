package com.geoshield.risk.dto;

import java.math.BigDecimal;

/**
 * Structured per-factor transparency detail for live risk calculation factors.
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
        String source) { }
