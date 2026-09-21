package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BaselineRiskResult(
        UUID decisionId,
        BigDecimal score,
        RiskLevel riskLevel,
        List<RiskFactorContribution> contributingFactors,
        RiskDataCompleteness dataCompleteness,
        List<RiskFactorDetail> factorDetails,
        String recommendation,
        String scoringMethod,
        String modelVersion) {

    public BaselineRiskResult(
            BigDecimal score,
            RiskLevel riskLevel,
            List<RiskFactorContribution> contributingFactors,
            RiskDataCompleteness dataCompleteness,
            List<RiskFactorDetail> factorDetails,
            String recommendation,
            String scoringMethod,
            String modelVersion) {
        this(UUID.randomUUID(), score, riskLevel, contributingFactors, dataCompleteness, factorDetails, recommendation, scoringMethod, modelVersion);
    }

    public BaselineRiskResult(
            BigDecimal score,
            RiskLevel riskLevel,
            List<RiskFactorContribution> contributingFactors,
            String recommendation,
            String scoringMethod,
            String modelVersion) {
        this(UUID.randomUUID(), score, riskLevel, contributingFactors, null, null, recommendation, scoringMethod, modelVersion);
    }
}
