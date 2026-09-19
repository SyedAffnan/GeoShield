package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.util.List;

/** Minimal client-facing contract for the implemented deterministic baseline path. */
public record RiskResponse(
        BigDecimal safetyScore,
        RiskLevel riskLevel,
        String recommendation,
        List<RiskFactorContribution> contributingFactors,
        RiskDataCompleteness dataCompleteness,
        List<RiskFactorDetail> factorDetails,
        String modelType,
        String modelVersion) {

    public RiskResponse(
            BigDecimal safetyScore,
            RiskLevel riskLevel,
            String recommendation,
            List<RiskFactorContribution> contributingFactors,
            String modelType,
            String modelVersion) {
        this(safetyScore, riskLevel, recommendation, contributingFactors, null, null, modelType, modelVersion);
    }
}
