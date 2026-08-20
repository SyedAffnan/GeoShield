package com.geoshield.risk.service;

import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.RiskFactorInput;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds only source-supported baseline inputs through module service boundaries. */
@Service
public class RiskContextAssembler {
    private final LocationService locationService;
    private final HistoricalDataService historicalDataService;
    private final IncidentService incidentService;

    public RiskContextAssembler(LocationService locationService, HistoricalDataService historicalDataService,
            IncidentService incidentService) {
        this.locationService = locationService;
        this.historicalDataService = historicalDataService;
        this.incidentService = incidentService;
    }

    @Transactional(readOnly = true)
    public BaselineRiskCalculationRequest assembleForCurrentUser(UUID userId) {
        // Enforces the approved precondition and avoids direct Location repository access.
        locationService.getCurrentLocation(userId);
        boolean historicalRecordsPresent = historicalDataService.hasHistoricalSafetyRecords();
        List<IncidentResponse> userReports = incidentService.getIncidents(userId);

        return new BaselineRiskCalculationRequest(userId,
                RiskFactorInput.unavailable(historicalReason(historicalRecordsPresent)),
                RiskFactorInput.unavailable("No approved weather provider is configured."),
                RiskFactorInput.unavailable("No approved time-of-day normalization rule is configured."),
                RiskFactorInput.unavailable("No emergency-service proximity data source is configured."),
                RiskFactorInput.unavailable(incidentReason(userReports)),
                RiskFactorInput.unavailable("No client connectivity input contract is configured."),
                RiskFactorInput.unavailable("No other approved contextual signal is available."));
    }

    private String historicalReason(boolean recordsPresent) {
        if (recordsPresent) {
            return "Historical records exist, but GPS-to-geographic mapping and normalization are not approved.";
        }
        return "No historical safety records are available.";
    }

    private String incidentReason(List<IncidentResponse> userReports) {
        if (!userReports.isEmpty()) {
            return "User reports exist, but the approved incident-normalization rule is not defined.";
        }
        return "No user-reported incidents are available for the authenticated user.";
    }
}
