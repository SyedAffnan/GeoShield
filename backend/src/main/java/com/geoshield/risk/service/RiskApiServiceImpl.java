package com.geoshield.risk.service;

import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskAssemblyContext;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskApiServiceImpl implements RiskApiService {
    private final RiskContextAssembler riskContextAssembler;
    private final RiskFusionService riskFusionService;
    private final LocationService locationService;
    private final SachetAlertService sachetAlertService;

    @Autowired
    public RiskApiServiceImpl(
            RiskContextAssembler riskContextAssembler,
            RiskFusionService riskFusionService,
            LocationService locationService,
            SachetAlertService sachetAlertService) {
        this.riskContextAssembler = riskContextAssembler;
        this.riskFusionService = riskFusionService;
        this.locationService = locationService;
        this.sachetAlertService = sachetAlertService;
    }

    public RiskApiServiceImpl(
            RiskContextAssembler riskContextAssembler,
            RiskFusionService riskFusionService) {
        this(riskContextAssembler, riskFusionService, null, null);
    }

    @Override
    @Transactional
    public RiskResponse getCurrentRisk(UUID userId) {
        // Obtain assembly context with baseline calculation inputs and location snapshot (avoids duplicate location lookup)
        RiskAssemblyContext assemblyContext = null;
        try {
            assemblyContext = riskContextAssembler.assembleContextForCurrentUser(userId);
        } catch (Exception ignored) {
        }

        BaselineRiskCalculationRequest baselineRequest;
        LocationResponse location = null;
        if (assemblyContext != null) {
            baselineRequest = assemblyContext.request();
            location = assemblyContext.location();
        } else {
            baselineRequest = riskContextAssembler.assembleForCurrentUser(userId);
            if (locationService != null) {
                location = locationService.getCurrentLocation(userId);
            }
        }

        // Compute and persist the independent 5-factor baseline risk
        BaselineRiskResult result = riskFusionService.calculateBaselineRisk(baselineRequest);

        // Check for active SACHET disaster alerts using the resolved location
        Optional<SachetAlertSummary> alertOpt = Optional.empty();
        if (sachetAlertService != null && location != null && location.latitude() != null && location.longitude() != null) {
            alertOpt = sachetAlertService.findApplicableActiveAlert(
                    location.latitude().doubleValue(),
                    location.longitude().doubleValue(),
                    Instant.now()
            );
        }

        boolean overrideActive = false;
        RiskLevel effectiveRiskLevel = result.riskLevel();
        String recommendation = result.recommendation();
        SachetAlertSummary activeAlert = null;

        if (alertOpt.isPresent() && sachetAlertService != null) {
            activeAlert = alertOpt.get();
            if (sachetAlertService.isQualifyingSevereAlert(activeAlert)) {
                overrideActive = true;
                effectiveRiskLevel = RiskLevel.CRITICAL;
                recommendation = "CIVIL DEFENSE DISASTER OVERRIDE: " + activeAlert.event() + " - "
                        + (activeAlert.instruction() != null && !activeAlert.instruction().isBlank()
                                ? activeAlert.instruction()
                                : (activeAlert.headline() != null ? activeAlert.headline() : activeAlert.event()));
            }
        }

        return new RiskResponse(
                result.score(),
                result.riskLevel(),
                recommendation,
                result.contributingFactors(),
                result.dataCompleteness(),
                result.factorDetails(),
                result.scoringMethod(),
                result.modelVersion(),
                overrideActive,
                effectiveRiskLevel,
                activeAlert
        );
    }
}
