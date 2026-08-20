package com.geoshield.risk.service;

import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskApiServiceImpl implements RiskApiService {
    private final RiskContextAssembler riskContextAssembler;
    private final RiskFusionService riskFusionService;

    public RiskApiServiceImpl(RiskContextAssembler riskContextAssembler, RiskFusionService riskFusionService) {
        this.riskContextAssembler = riskContextAssembler;
        this.riskFusionService = riskFusionService;
    }

    @Override
    @Transactional
    public RiskResponse getCurrentRisk(UUID userId) {
        BaselineRiskResult result = riskFusionService.calculateBaselineRisk(riskContextAssembler.assembleForCurrentUser(userId));
        return new RiskResponse(result.score(), result.riskLevel(), result.recommendation(), result.contributingFactors(),
                result.scoringMethod(), result.modelVersion());
    }
}
