package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaselineRiskFusionServiceTest {
    @Mock private IdentityService identityService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private User user;
    private BaselineRiskFusionService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        service = new BaselineRiskFusionService(properties(), identityService, riskScoreRepository, new ObjectMapper());
        lenient().when(identityService.getUserById(userId)).thenReturn(user);
    }

    @Test
    void calculatesZeroWhenAllAvailableComponentsAreZero() {
        BaselineRiskResult result = service.calculateBaselineRisk(request(available(0)));
        assertEquals(new BigDecimal("0.00"), result.score().setScale(2));
        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals("Normal precautions are recommended.", result.recommendation());
    }

    @Test
    void calculatesOneHundredWhenAllComponentsAreOneHundred() {
        BaselineRiskResult result = service.calculateBaselineRisk(request(available(100)));
        assertEquals(0, result.score().compareTo(BigDecimal.valueOf(100)));
        assertEquals(RiskLevel.CRITICAL, result.riskLevel());
    }

    @Test
    void appliesApprovedWeightsWithoutRoundingIntermediateContributions() {
        BaselineRiskResult result = service.calculateBaselineRisk(new BaselineRiskCalculationRequest(userId,
                available(40), available(50), available(60), available(20), available(10), available(80), available(0)));
        assertEquals(0, result.score().compareTo(new BigDecimal("39.00")));
        assertEquals(0, result.contributingFactors().getFirst().contribution().compareTo(new BigDecimal("12.00")));
        assertEquals(0, result.contributingFactors().get(1).contribution().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void classifiesApprovedBoundaryScores() {
        assertEquals(RiskLevel.LOW, service.calculateBaselineRisk(request(available(39))).riskLevel());
        assertEquals(RiskLevel.MEDIUM, service.calculateBaselineRisk(request(available(40))).riskLevel());
        assertEquals(RiskLevel.MEDIUM, service.calculateBaselineRisk(request(available(59))).riskLevel());
        assertEquals(RiskLevel.HIGH, service.calculateBaselineRisk(request(available(60))).riskLevel());
        assertEquals(RiskLevel.HIGH, service.calculateBaselineRisk(request(available(79))).riskLevel());
        assertEquals(RiskLevel.CRITICAL, service.calculateBaselineRisk(request(available(80))).riskLevel());
    }

    @Test
    void returnsDeterministicRecommendationForEachRiskLevel() {
        assertEquals("Exercise increased caution and remain aware of your surroundings.",
                service.calculateBaselineRisk(request(available(40))).recommendation());
        assertEquals("Avoid unnecessary travel in this area and stay in safer, well-populated locations.",
                service.calculateBaselineRisk(request(available(60))).recommendation());
        assertEquals("Avoid the area if possible and seek a safer location.",
                service.calculateBaselineRisk(request(available(80))).recommendation());
    }

    @Test
    void preservesHistoricalAndUserReportComponentsForExplainabilityAndAudit() {
        BaselineRiskResult result = service.calculateBaselineRisk(new BaselineRiskCalculationRequest(userId,
                available(40), unavailable(), unavailable(), unavailable(), available(20), unavailable(), unavailable()));
        assertEquals(0, result.score().compareTo(new BigDecimal("14.00")));
        ArgumentCaptor<RiskScore> saved = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(saved.capture());
        assertEquals(14, saved.getValue().getScore());
        assertTrue(saved.getValue().getContributingFactors().contains("HISTORICAL_INCIDENT"));
        assertTrue(saved.getValue().getContributingFactors().contains("USER_REPORT"));
    }

    @Test
    void treatsUnavailableInputsAsUnavailableWithoutFabricatingScores() {
        BaselineRiskResult result = service.calculateBaselineRisk(request(unavailable()));
        assertEquals(0, result.score().compareTo(BigDecimal.ZERO));
        assertTrue(result.contributingFactors().stream().allMatch(factor -> !factor.available()));
        assertTrue(result.contributingFactors().stream().allMatch(factor -> factor.normalizedRisk() == null));
        assertTrue(result.contributingFactors().stream().allMatch(factor -> factor.contribution().signum() == 0));
    }

    @Test
    void rejectsOutOfRangeAndFabricatedUnavailableInputs() {
        assertThrows(IllegalArgumentException.class, () -> RiskFactorInput.available(BigDecimal.valueOf(101)));
        assertThrows(IllegalArgumentException.class, () -> new RiskFactorInput(BigDecimal.ONE, false));
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        assertThrows(IllegalArgumentException.class, () -> new RiskFusionProperties(new BigDecimal("0.31"), new BigDecimal("0.20"),
                new BigDecimal("0.15"), new BigDecimal("0.15"), new BigDecimal("0.10"), new BigDecimal("0.05"),
                new BigDecimal("0.05"), 39, 59, 79));
    }

    private BaselineRiskCalculationRequest request(RiskFactorInput input) {
        return new BaselineRiskCalculationRequest(userId, input, input, input, input, input, input, input);
    }
    private RiskFactorInput available(int value) { return RiskFactorInput.available(BigDecimal.valueOf(value)); }
    private RiskFactorInput unavailable() { return RiskFactorInput.unavailable(); }
    private RiskFusionProperties properties() { return new RiskFusionProperties(new BigDecimal("0.30"), new BigDecimal("0.20"),
            new BigDecimal("0.15"), new BigDecimal("0.15"), new BigDecimal("0.10"), new BigDecimal("0.05"),
            new BigDecimal("0.05"), 39, 59, 79); }
}
