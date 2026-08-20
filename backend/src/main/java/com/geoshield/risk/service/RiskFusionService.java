package com.geoshield.risk.service;
import com.geoshield.common.service.ModuleService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
/** Approved seam for the explainable weighted risk-fusion implementation. */
public interface RiskFusionService extends ModuleService {
    BaselineRiskResult calculateBaselineRisk(BaselineRiskCalculationRequest request);

    // TODO(architecture-open): add the approved public Risk API contract once its response is finalized.
    // TODO(architecture-open): add geographic normalization before resolving GPS coordinates to historical data.
    // TODO(architecture-open): define evidence-based normalization rules for raw contextual source data.
    // TODO(architecture-open): add a validated AI/ML strategy behind this interface after model training is approved.
}
