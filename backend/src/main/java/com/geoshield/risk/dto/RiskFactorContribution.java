package com.geoshield.risk.dto;

import java.math.BigDecimal;

public record RiskFactorContribution(
        RiskFactorType factor,
        boolean available,
        BigDecimal normalizedRisk,
        BigDecimal weight,
        BigDecimal contribution,
        String explanation,
        String reasonCode,
        String rawValue) {

    public RiskFactorContribution(
            RiskFactorType factor,
            boolean available,
            BigDecimal normalizedRisk,
            BigDecimal weight,
            BigDecimal contribution,
            String explanation) {
        this(factor, available, normalizedRisk, weight, contribution, explanation, null, null);
    }
}
