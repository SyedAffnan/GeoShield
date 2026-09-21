package com.geoshield.risk.dto;

import com.geoshield.notification.dto.SachetAlertSummary;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Minimal client-facing contract for the implemented deterministic baseline path. */
public record RiskResponse(
        UUID decisionId,
        BigDecimal safetyScore,
        RiskLevel riskLevel,
        String recommendation,
        List<RiskFactorContribution> contributingFactors,
        RiskDataCompleteness dataCompleteness,
        List<RiskFactorDetail> factorDetails,
        String modelType,
        String modelVersion,
        boolean overrideActive,
        RiskLevel effectiveRiskLevel,
        SachetAlertSummary activeDisasterAlert) {

    public RiskResponse(
            BigDecimal safetyScore,
            RiskLevel riskLevel,
            String recommendation,
            List<RiskFactorContribution> contributingFactors,
            RiskDataCompleteness dataCompleteness,
            List<RiskFactorDetail> factorDetails,
            String modelType,
            String modelVersion,
            boolean overrideActive,
            RiskLevel effectiveRiskLevel,
            SachetAlertSummary activeDisasterAlert) {
        this(null, safetyScore, riskLevel, recommendation, contributingFactors, dataCompleteness, factorDetails, modelType, modelVersion, overrideActive, effectiveRiskLevel, activeDisasterAlert);
    }

    public RiskResponse(
            BigDecimal safetyScore,
            RiskLevel riskLevel,
            String recommendation,
            List<RiskFactorContribution> contributingFactors,
            RiskDataCompleteness dataCompleteness,
            List<RiskFactorDetail> factorDetails,
            String modelType,
            String modelVersion) {
        this(null, safetyScore, riskLevel, recommendation, contributingFactors, dataCompleteness, factorDetails, modelType, modelVersion, false, riskLevel, null);
    }

    public RiskResponse(
            BigDecimal safetyScore,
            RiskLevel riskLevel,
            String recommendation,
            List<RiskFactorContribution> contributingFactors,
            String modelType,
            String modelVersion) {
        this(null, safetyScore, riskLevel, recommendation, contributingFactors, null, null, modelType, modelVersion, false, riskLevel, null);
    }
}
