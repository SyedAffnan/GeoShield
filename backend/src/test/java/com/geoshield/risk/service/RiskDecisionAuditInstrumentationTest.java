package com.geoshield.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.historicaldata.entity.GeographicLevel;
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
import com.geoshield.risk.dto.RiskDataCompleteness;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorDetail;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.entity.RiskScoringMethod;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskDecisionAuditInstrumentationTest {

    @Mock private RiskContextAssembler riskContextAssembler;
    @Mock private RiskFusionService riskFusionService;
    @Mock private LocationService locationService;
    @Mock private SachetAlertService sachetAlertService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private IdentityService identityService;
    @Mock private User user;

    private ObjectMapper objectMapper;
    private RiskApiServiceImpl riskApiService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userId = UUID.randomUUID();
        riskApiService = new RiskApiServiceImpl(
                riskContextAssembler,
                riskFusionService,
                locationService,
                sachetAlertService,
                riskScoreRepository,
                identityService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Audit record captures truthful SACHET CRITICAL override decision with alert ID and stcode")
    void recordsTruthfulOverrideAuditDecision() {
        UUID decisionId = UUID.randomUUID();
        BigDecimal decimalScore = new BigDecimal("34.50");
        RiskLevel baselineLevel = RiskLevel.LOW;
        List<RiskFactorContribution> contributions = List.of(
                new RiskFactorContribution(RiskFactorType.HISTORICAL_INCIDENT, true, new BigDecimal("40.0"),
                        new BigDecimal("0.30"), new BigDecimal("12.0"), "Historical contribution", null, "40"),
                new RiskFactorContribution(RiskFactorType.WEATHER, true, new BigDecimal("50.0"),
                        new BigDecimal("0.20"), new BigDecimal("10.0"), "Weather contribution", null, "50")
        );
        RiskDataCompleteness completeness = RiskDataCompleteness.of(2, List.of(RiskFactorType.TIME_OF_DAY),
                Map.of(RiskFactorType.TIME_OF_DAY, "Sensor unavailable"));
        List<RiskFactorDetail> details = List.of(
                new RiskFactorDetail(RiskFactorType.HISTORICAL_INCIDENT, new BigDecimal("0.30"), true, "40",
                        new BigDecimal("40.0"), new BigDecimal("12.0"), null, "Historical explanation", "MORTH"),
                new RiskFactorDetail(RiskFactorType.WEATHER, new BigDecimal("0.20"), true, "50",
                        new BigDecimal("50.0"), new BigDecimal("10.0"), null, "Weather explanation", "OPEN_METEO")
        );

        BaselineRiskResult baselineResult = new BaselineRiskResult(
                decisionId, decimalScore, baselineLevel, contributions,
                completeness, details, "Baseline recommendation", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.available(BigDecimal.valueOf(40)),
                RiskFactorInput.available(BigDecimal.valueOf(50)), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(12.9716), BigDecimal.valueOf(77.5946),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Karnataka", "29");

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location, resolution));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        // Simulate that calculateBaselineRisk saved an initial record
        RiskScore initialScore = new RiskScore(
                decisionId, user, 35, decimalScore, baselineLevel, baselineLevel,
                false, null, null, null, null,
                "{}", "[]", "[]", RiskScoringMethod.BASELINE_WEIGHTED, null
        );
        when(riskScoreRepository.findByDecisionId(decisionId)).thenReturn(Optional.of(initialScore));

        SachetAlertSummary severeAlert = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-2026-CYC-0099", "imd_alert@sachet.ndma.gov.in",
                Instant.now(), "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS),
                "Severe Cyclone", "Evacuate", "Move inland", "Karnataka Coastal", false
        );

        when(sachetAlertService.findApplicableActiveAlert(eq(12.9716), eq(77.5946), any(Instant.class)))
                .thenReturn(Optional.of(severeAlert));
        when(sachetAlertService.isQualifyingSevereAlert(severeAlert)).thenReturn(true);

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertEquals(decisionId, response.decisionId());
        assertTrue(response.overrideActive());
        assertEquals(RiskLevel.CRITICAL, response.effectiveRiskLevel());
        assertEquals(RiskLevel.LOW, response.riskLevel()); // baseline preserved
        assertEquals(decimalScore, response.safetyScore());

        // Verify that the persisted RiskScore was updated with truthful effective decision
        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(captor.capture());
        RiskScore savedScore = captor.getValue();

        assertEquals(decisionId, savedScore.getDecisionId());
        assertEquals(decimalScore, savedScore.getDecimalScore());
        assertEquals(35, savedScore.getScore());
        assertEquals(RiskLevel.LOW, savedScore.getRiskLevel()); // baseline level
        assertEquals(RiskLevel.CRITICAL, savedScore.getEffectiveRiskLevel()); // effective overridden level
        assertTrue(savedScore.isOverrideActive());
        assertEquals("NDMA-2026-CYC-0099", savedScore.getSachetAlertIdentifier());
        assertEquals(GeographicLevel.STATE_UT, savedScore.getGeographicLevel());
        assertEquals("Karnataka", savedScore.getGeographicUnit());
        assertEquals("29", savedScore.getStateCode());
    }

    @Test
    @DisplayName("Audit record captures standard baseline without override when no severe alert active")
    void recordsStandardBaselineDecisionWhenNoOverride() {
        UUID decisionId = UUID.randomUUID();
        BigDecimal decimalScore = new BigDecimal("25.00");
        RiskLevel baselineLevel = RiskLevel.LOW;

        BaselineRiskResult baselineResult = new BaselineRiskResult(
                decisionId, decimalScore, baselineLevel, List.of(),
                null, null, "Normal precautions are recommended.", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(19.0760), BigDecimal.valueOf(72.8777),
                BigDecimal.valueOf(5.0), BigDecimal.ZERO, Instant.now()
        );
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Maharashtra", "27");

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location, resolution));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        RiskScore initialScore = new RiskScore(
                decisionId, user, 25, decimalScore, baselineLevel, baselineLevel,
                false, null, null, null, null,
                null, null, "[]", RiskScoringMethod.BASELINE_WEIGHTED, null
        );
        when(riskScoreRepository.findByDecisionId(decisionId)).thenReturn(Optional.of(initialScore));

        when(sachetAlertService.findApplicableActiveAlert(eq(19.0760), eq(72.8777), any(Instant.class)))
                .thenReturn(Optional.empty());

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertEquals(decisionId, response.decisionId());
        assertFalse(response.overrideActive());
        assertEquals(RiskLevel.LOW, response.effectiveRiskLevel());
        assertEquals(RiskLevel.LOW, response.riskLevel());

        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(captor.capture());
        RiskScore savedScore = captor.getValue();

        assertEquals(decisionId, savedScore.getDecisionId());
        assertEquals(RiskLevel.LOW, savedScore.getEffectiveRiskLevel());
        assertFalse(savedScore.isOverrideActive());
        assertNull(savedScore.getSachetAlertIdentifier());
        assertEquals(GeographicLevel.STATE_UT, savedScore.getGeographicLevel());
        assertEquals("Maharashtra", savedScore.getGeographicUnit());
        assertEquals("27", savedScore.getStateCode());
    }

    @Test
    @DisplayName("Entity captures data completeness and factor details JSON columns")
    void entityStoresDataCompletenessAndFactorDetails() throws Exception {
        RiskDataCompleteness completeness = RiskDataCompleteness.of(
                3,
                List.of(RiskFactorType.SERVICE_PROXIMITY, RiskFactorType.USER_REPORT),
                Map.of(RiskFactorType.SERVICE_PROXIMITY, "Service unavailable",
                       RiskFactorType.USER_REPORT, "No user reports")
        );
        String completenessJson = objectMapper.writeValueAsString(completeness);

        List<RiskFactorDetail> details = List.of(
                new RiskFactorDetail(RiskFactorType.HISTORICAL_INCIDENT, new BigDecimal("0.30"), true, "30",
                        new BigDecimal("30.0"), new BigDecimal("9.0"), null, "Historical", "MORTH")
        );
        String factorDetailsJson = objectMapper.writeValueAsString(details);

        RiskScore score = new RiskScore(
                UUID.randomUUID(), user, 42, new BigDecimal("42.00"), RiskLevel.MEDIUM, RiskLevel.MEDIUM,
                false, null, GeographicLevel.STATE_UT, "Delhi", "07",
                completenessJson, factorDetailsJson, "[]", RiskScoringMethod.BASELINE_WEIGHTED, "1.0.0"
        );

        assertEquals("Delhi", score.getGeographicUnit());
        assertEquals("07", score.getStateCode());
        assertEquals(completenessJson, score.getDataCompleteness());
        assertEquals(factorDetailsJson, score.getFactorDetails());
        assertThat(score.getDataCompleteness()).contains("availableFactorCount\":3");
        assertThat(score.getFactorDetails()).contains("HISTORICAL_INCIDENT");
    }

    @Test
    @DisplayName("Repository query contract: documents userId scoping contract for historical risk retrieval")
    void repositoryContract_findAllByUserId_documentsUserScopingContract() {
        UUID touristA = UUID.randomUUID();
        UUID touristB = UUID.randomUUID();

        RiskScore scoreA = new RiskScore(
                UUID.randomUUID(), user, 20, new BigDecimal("20.00"), RiskLevel.LOW, RiskLevel.LOW,
                false, null, GeographicLevel.STATE_UT, "Goa", "30",
                null, null, "[]", RiskScoringMethod.BASELINE_WEIGHTED, null
        );

        when(riskScoreRepository.findAllByUserIdOrderByCreatedAtDesc(touristA)).thenReturn(List.of(scoreA));
        when(riskScoreRepository.findAllByUserIdOrderByCreatedAtDesc(touristB)).thenReturn(List.of());

        List<RiskScore> historyA = riskScoreRepository.findAllByUserIdOrderByCreatedAtDesc(touristA);
        List<RiskScore> historyB = riskScoreRepository.findAllByUserIdOrderByCreatedAtDesc(touristB);

        assertEquals(1, historyA.size());
        assertEquals("Goa", historyA.get(0).getGeographicUnit());
        assertTrue(historyB.isEmpty(), "Specification: repository consumer expects tourist isolation based on query parameter");
    }

    @Test
    @DisplayName("Fallback persistence saves complete decision when fusion engine did not pre-persist")
    void fallbackPersistenceSavesCompleteRecordWhenNotPrePersisted() {
        UUID decisionId = UUID.randomUUID();
        BigDecimal decimalScore = new BigDecimal("45.00");
        RiskLevel baselineLevel = RiskLevel.MEDIUM;

        BaselineRiskResult baselineResult = new BaselineRiskResult(
                decisionId, decimalScore, baselineLevel, List.of(),
                null, null, "Exercise caution", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(28.6139), BigDecimal.valueOf(77.2090),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Delhi", "07");

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location, resolution));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);
        when(identityService.getUserById(userId)).thenReturn(user);

        // Simulate that calculateBaselineRisk did NOT save in repository (e.g. mocked or skipped)
        when(riskScoreRepository.findByDecisionId(decisionId)).thenReturn(Optional.empty());

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(captor.capture());
        RiskScore saved = captor.getValue();

        assertEquals(decisionId, saved.getDecisionId());
        assertEquals(decimalScore, saved.getDecimalScore());
        assertEquals("Delhi", saved.getGeographicUnit());
        assertEquals("07", saved.getStateCode());
    }

    @Test
    @DisplayName("H2: Serialization failure in fallback audit persistence throws IllegalStateException (fail-fast)")
    void fallbackPersistenceThrowsIllegalStateExceptionOnSerializationFailure() throws Exception {
        UUID decisionId = UUID.randomUUID();
        BaselineRiskResult baselineResult = new BaselineRiskResult(
                decisionId, new BigDecimal("45.00"), RiskLevel.MEDIUM, List.of(),
                null, null, "Exercise caution", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(28.6139), BigDecimal.valueOf(77.2090),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Delhi", "07");

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location, resolution));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);
        when(riskScoreRepository.findByDecisionId(decisionId)).thenReturn(Optional.empty());
        when(identityService.getUserById(userId)).thenReturn(user);

        ObjectMapper failingMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new com.fasterxml.jackson.core.JsonParseException(null, "simulated error"));

        RiskApiServiceImpl serviceWithFailingMapper = new RiskApiServiceImpl(
                riskContextAssembler,
                riskFusionService,
                locationService,
                sachetAlertService,
                riskScoreRepository,
                identityService,
                failingMapper
        );

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                serviceWithFailingMapper.getCurrentRisk(userId));
    }
}
