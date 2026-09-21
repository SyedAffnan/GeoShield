package com.geoshield.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.RiskAssemblyContext;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.entity.RiskScoringMethod;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskApiServiceImpl implements RiskApiService {
    private static final Logger log = LoggerFactory.getLogger(RiskApiServiceImpl.class);

    private final RiskContextAssembler riskContextAssembler;
    private final RiskFusionService riskFusionService;
    private final LocationService locationService;
    private final SachetAlertService sachetAlertService;
    private final RiskScoreRepository riskScoreRepository;
    private final IdentityService identityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public RiskApiServiceImpl(
            RiskContextAssembler riskContextAssembler,
            RiskFusionService riskFusionService,
            LocationService locationService,
            SachetAlertService sachetAlertService,
            @Autowired(required = false) RiskScoreRepository riskScoreRepository,
            @Autowired(required = false) IdentityService identityService,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.riskContextAssembler = riskContextAssembler;
        this.riskFusionService = riskFusionService;
        this.locationService = locationService;
        this.sachetAlertService = sachetAlertService;
        this.riskScoreRepository = riskScoreRepository;
        this.identityService = identityService;
        this.objectMapper = objectMapper;
    }

    public RiskApiServiceImpl(
            RiskContextAssembler riskContextAssembler,
            RiskFusionService riskFusionService,
            LocationService locationService,
            SachetAlertService sachetAlertService) {
        this(riskContextAssembler, riskFusionService, locationService, sachetAlertService, null, null, null);
    }

    public RiskApiServiceImpl(
            RiskContextAssembler riskContextAssembler,
            RiskFusionService riskFusionService) {
        this(riskContextAssembler, riskFusionService, null, null, null, null, null);
    }

    @Override
    @Transactional
    public RiskResponse getCurrentRisk(UUID userId) {
        // Obtain assembly context with baseline calculation inputs, location snapshot, and geographic resolution
        RiskAssemblyContext assemblyContext = riskContextAssembler.assembleContextForCurrentUser(userId);
        if (assemblyContext == null) {
            // Backward-compatibility fallback when mock tests directly stub assembleForCurrentUser
            log.debug("assembleContextForCurrentUser returned null for user {}, falling back to assembleForCurrentUser", userId);
            BaselineRiskCalculationRequest request = riskContextAssembler.assembleForCurrentUser(userId);
            LocationResponse location = (locationService != null) ? locationService.getCurrentLocation(userId) : null;
            assemblyContext = new RiskAssemblyContext(request, location);
        }

        BaselineRiskCalculationRequest baselineRequest = assemblyContext.request();
        LocationResponse location = assemblyContext.location();
        GeographicResolution resolution = assemblyContext.resolution();

        // Compute baseline risk (persisted with decisionId by fusion engine)
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

        // Finalize risk audit persistence with truthful effective decision details
        if (riskScoreRepository != null) {
            final boolean finalOverride = overrideActive;
            final RiskLevel finalEffectiveLevel = effectiveRiskLevel;
            final SachetAlertSummary finalAlert = activeAlert;
            Optional<RiskScore> existing = result.decisionId() != null
                    ? riskScoreRepository.findByDecisionId(result.decisionId())
                    : Optional.empty();
            if (existing.isPresent()) {
                RiskScore score = existing.get();
                score.recordDecisionDetails(
                        finalEffectiveLevel,
                        finalOverride,
                        finalAlert != null ? finalAlert.identifier() : null,
                        resolution != null ? resolution.geographicLevel() : null,
                        resolution != null ? resolution.geographicUnit() : null,
                        resolution != null ? resolution.stateCode() : null
                );
                riskScoreRepository.save(score);
            } else if (identityService != null && objectMapper != null) {
                persistAuditRecord(userId, result, finalEffectiveLevel, finalOverride, finalAlert, resolution);
            }
        }

        return new RiskResponse(
                result.decisionId(),
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

    private void persistAuditRecord(
            UUID userId,
            BaselineRiskResult result,
            RiskLevel effectiveRiskLevel,
            boolean overrideActive,
            SachetAlertSummary alert,
            GeographicResolution resolution) {
        User user = identityService.getUserById(userId);
        try {
            String factors = objectMapper.writeValueAsString(result.contributingFactors());
            String completenessJson = result.dataCompleteness() != null
                    ? objectMapper.writeValueAsString(result.dataCompleteness())
                    : null;
            String factorDetailsJson = result.factorDetails() != null
                    ? objectMapper.writeValueAsString(result.factorDetails())
                    : null;
            int persistedScore = result.score().setScale(0, RoundingMode.HALF_UP).intValueExact();

            RiskScore score = new RiskScore(
                    result.decisionId(),
                    user,
                    persistedScore,
                    result.score(),
                    result.riskLevel(),
                    effectiveRiskLevel,
                    overrideActive,
                    alert != null ? alert.identifier() : null,
                    resolution != null ? resolution.geographicLevel() : null,
                    resolution != null ? resolution.geographicUnit() : null,
                    resolution != null ? resolution.stateCode() : null,
                    completenessJson,
                    factorDetailsJson,
                    factors,
                    RiskScoringMethod.BASELINE_WEIGHTED,
                    result.modelVersion()
            );
            riskScoreRepository.save(score);
        } catch (JsonProcessingException ex) {
            log.error("Unable to serialize risk audit details for user {}", userId, ex);
        }
    }
}
