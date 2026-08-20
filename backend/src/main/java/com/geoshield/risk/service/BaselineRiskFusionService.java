package com.geoshield.risk.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.entity.RiskScoringMethod;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaselineRiskFusionService implements RiskFusionService {
    private static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);
    private static final String BASELINE_METHOD = "BASELINE_WEIGHTED";

    private final RiskFusionProperties properties;
    private final IdentityService identityService;
    private final RiskScoreRepository riskScoreRepository;
    private final ObjectMapper objectMapper;

    public BaselineRiskFusionService(RiskFusionProperties properties, IdentityService identityService,
            RiskScoreRepository riskScoreRepository, ObjectMapper objectMapper) {
        this.properties = properties;
        this.identityService = identityService;
        this.riskScoreRepository = riskScoreRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public BaselineRiskResult calculateBaselineRisk(BaselineRiskCalculationRequest request) {
        List<RiskFactorContribution> factors = List.of(
                contribution(RiskFactorType.HISTORICAL_INCIDENT, request.historicalIncidentRisk(), properties.historicalIncidentWeight()),
                contribution(RiskFactorType.WEATHER, request.weatherRisk(), properties.weatherWeight()),
                contribution(RiskFactorType.TIME_OF_DAY, request.timeOfDayRisk(), properties.timeOfDayWeight()),
                contribution(RiskFactorType.SERVICE_PROXIMITY, request.serviceProximityRisk(), properties.serviceProximityWeight()),
                contribution(RiskFactorType.USER_REPORT, request.userReportRisk(), properties.userReportWeight()),
                contribution(RiskFactorType.CONNECTIVITY, request.connectivityRisk(), properties.connectivityWeight()),
                contribution(RiskFactorType.OTHER_CONTEXT, request.otherContextRisk(), properties.otherContextWeight()));
        BigDecimal score = clamp(factors.stream().map(RiskFactorContribution::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        RiskLevel riskLevel = classify(score);
        BaselineRiskResult result = new BaselineRiskResult(score, riskLevel, factors, recommendation(riskLevel), BASELINE_METHOD, null);
        persist(request.userId(), result);
        return result;
    }

    private RiskFactorContribution contribution(RiskFactorType factor, RiskFactorInput input, BigDecimal weight) {
        if (!input.available()) {
            return new RiskFactorContribution(factor, false, null, weight, BigDecimal.ZERO,
                    factorLabel(factor) + " is unavailable and contributes no fabricated risk value.");
        }
        BigDecimal contribution = input.normalizedRisk().multiply(weight);
        return new RiskFactorContribution(factor, true, input.normalizedRisk(), weight, contribution,
                factorLabel(factor) + " contributed " + contribution.stripTrailingZeros().toPlainString() + " risk points.");
    }

    private void persist(java.util.UUID userId, BaselineRiskResult result) {
        User user = identityService.getUserById(userId);
        try {
            String factors = objectMapper.writeValueAsString(result.contributingFactors());
            int persistedScore = result.score().setScale(0, RoundingMode.HALF_UP).intValueExact();
            riskScoreRepository.save(new RiskScore(user, persistedScore, result.riskLevel(), factors,
                    RiskScoringMethod.BASELINE_WEIGHTED, null));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize baseline risk explanations", exception);
        }
    }

    private RiskLevel classify(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(properties.lowMax())) <= 0) return RiskLevel.LOW;
        if (score.compareTo(BigDecimal.valueOf(properties.mediumMax())) <= 0) return RiskLevel.MEDIUM;
        if (score.compareTo(BigDecimal.valueOf(properties.highMax())) <= 0) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    private BigDecimal clamp(BigDecimal score) {
        return score.max(MIN_SCORE).min(MAX_SCORE);
    }

    private String recommendation(RiskLevel level) {
        return switch (level) {
            case LOW -> "Normal precautions are recommended.";
            case MEDIUM -> "Exercise increased caution and remain aware of your surroundings.";
            case HIGH -> "Avoid unnecessary travel in this area and stay in safer, well-populated locations.";
            case CRITICAL -> "Avoid the area if possible and seek a safer location.";
        };
    }

    private String factorLabel(RiskFactorType factor) {
        return switch (factor) {
            case HISTORICAL_INCIDENT -> "Historical incident data";
            case WEATHER -> "Weather data";
            case TIME_OF_DAY -> "Time-of-day data";
            case SERVICE_PROXIMITY -> "Emergency-service proximity data";
            case USER_REPORT -> "User-report data";
            case CONNECTIVITY -> "Connectivity data";
            case OTHER_CONTEXT -> "Other contextual data";
        };
    }
}
