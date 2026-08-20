package com.geoshield.risk.dto;

import java.util.UUID;

/** HTTP-independent baseline feature vector. Each factor is supplied only by an approved source. */
public record BaselineRiskCalculationRequest(UUID userId, RiskFactorInput historicalIncidentRisk,
        RiskFactorInput weatherRisk, RiskFactorInput timeOfDayRisk, RiskFactorInput serviceProximityRisk,
        RiskFactorInput userReportRisk, RiskFactorInput connectivityRisk, RiskFactorInput otherContextRisk) {
    public BaselineRiskCalculationRequest {
        if (userId == null || historicalIncidentRisk == null || weatherRisk == null || timeOfDayRisk == null
                || serviceProximityRisk == null || userReportRisk == null || connectivityRisk == null
                || otherContextRisk == null) {
            throw new IllegalArgumentException("A user and all explicit risk-factor availability states are required");
        }
    }
}
