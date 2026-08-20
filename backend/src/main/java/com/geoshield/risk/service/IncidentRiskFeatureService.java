package com.geoshield.risk.service;

import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IncidentRiskFeatureService {
    public NormalizedRiskFeature userReportRisk(List<IncidentResponse> incidents) {
        String reason = incidents.isEmpty() ? "No user-reported incidents are available for the authenticated user."
                : "User-reported incidents exist, but no approved recent-window or count-to-risk normalization rule exists.";
        return NormalizedRiskFeature.unavailable(RiskFactorType.USER_REPORT, "Incident module", reason,
                "TODO(architecture-open): define incident recency and count normalization.");
    }
}
