package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskApiServiceImplTest {
    @Mock private RiskContextAssembler riskContextAssembler;
    @Mock private RiskFusionService riskFusionService;

    @Test
    void delegatesAuthenticatedUsersContextToBaselineAndPreservesBaselineResponse() {
        UUID userId = UUID.randomUUID();
        BaselineRiskCalculationRequest context = new BaselineRiskCalculationRequest(userId, RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(), RiskFactorInput.unavailable());
        BaselineRiskResult baseline = new BaselineRiskResult(new BigDecimal("42.50"), RiskLevel.MEDIUM,
                List.of(new RiskFactorContribution(RiskFactorType.WEATHER, false, null, new BigDecimal("0.20"),
                        BigDecimal.ZERO, "Weather data is unavailable.")),
                "Exercise increased caution and remain aware of your surroundings.", "BASELINE_WEIGHTED", null);
        when(riskContextAssembler.assembleForCurrentUser(userId)).thenReturn(context);
        when(riskFusionService.calculateBaselineRisk(context)).thenReturn(baseline);

        var response = new RiskApiServiceImpl(riskContextAssembler, riskFusionService).getCurrentRisk(userId);

        verify(riskContextAssembler).assembleForCurrentUser(userId);
        verify(riskFusionService).calculateBaselineRisk(context);
        assertEquals(new BigDecimal("42.50"), response.safetyScore());
        assertEquals(RiskLevel.MEDIUM, response.riskLevel());
        assertEquals("BASELINE_WEIGHTED", response.modelType());
        assertEquals(null, response.modelVersion());
        assertEquals(1, response.contributingFactors().size());
    }
}
