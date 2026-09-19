package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.util.List;

public record BaselineRiskResult(
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
            String recommendation,
            String scoringMethod,
            String modelVersion) {
        this(score, riskLevel, contributingFactors, null, null, recommendation, scoringMethod, modelVersion);
    }
}
