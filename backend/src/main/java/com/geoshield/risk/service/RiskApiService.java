package com.geoshield.risk.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.risk.dto.RiskResponse;
import java.util.UUID;

public interface RiskApiService extends ModuleService {
    RiskResponse getCurrentRisk(UUID userId);
}
