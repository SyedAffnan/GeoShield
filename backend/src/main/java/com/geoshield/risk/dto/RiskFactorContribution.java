package com.geoshield.risk.dto;

import java.math.BigDecimal;

public record RiskFactorContribution(RiskFactorType factor, boolean available, BigDecimal normalizedRisk,
        BigDecimal weight, BigDecimal contribution, String explanation) { }
