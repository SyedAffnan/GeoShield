package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.util.List;

/** Minimal client-facing contract for the implemented deterministic baseline path. */
public record RiskResponse(BigDecimal safetyScore, RiskLevel riskLevel, String recommendation,
        List<RiskFactorContribution> contributingFactors, String modelType, String modelVersion) { }
