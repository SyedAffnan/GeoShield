package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.util.List;

public record BaselineRiskResult(BigDecimal score, RiskLevel riskLevel, List<RiskFactorContribution> contributingFactors,
        String recommendation, String scoringMethod, String modelVersion) { }
