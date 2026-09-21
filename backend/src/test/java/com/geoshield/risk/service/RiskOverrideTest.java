package com.geoshield.risk.service;

import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskAssemblyContext;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskOverrideTest {

    @Mock
    private RiskContextAssembler riskContextAssembler;

    @Mock
    private RiskFusionService riskFusionService;

    @Mock
    private LocationService locationService;

    @Mock
    private SachetAlertService sachetAlertService;

    private RiskApiServiceImpl riskApiService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        riskApiService = new RiskApiServiceImpl(
                riskContextAssembler,
                riskFusionService,
                locationService,
                sachetAlertService
        );
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Active severe disaster alert triggers CRITICAL override while preserving baseline score and factors")
    void activeSevereAlertTriggersCriticalOverride() {
        BigDecimal baselineScore = new BigDecimal("35.00");
        RiskLevel baselineLevel = RiskLevel.LOW;
        List<RiskFactorContribution> contributions = List.of(
                new RiskFactorContribution(RiskFactorType.WEATHER, false, null, new BigDecimal("0.20"),
                        BigDecimal.ZERO, "Weather data is unavailable.")
        );

        BaselineRiskResult baselineResult = new BaselineRiskResult(
                baselineScore, baselineLevel, contributions,
                "Normal baseline recommendation", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(19.81), BigDecimal.valueOf(85.83),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        SachetAlertSummary severeAlert = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-2026-CYC-0042", "imd_alert@sachet.ndma.gov.in",
                Instant.now(), "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS),
                "Super Cyclone Warning", "Very heavy rains and storm surge", "Evacuate immediately to storm shelter",
                "Coastal Puri", false
        );

        when(sachetAlertService.findApplicableActiveAlert(eq(19.81), eq(85.83), any(Instant.class)))
                .thenReturn(Optional.of(severeAlert));
        when(sachetAlertService.isQualifyingSevereAlert(severeAlert)).thenReturn(true);

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertTrue(response.overrideActive(), "Override must be active");
        assertEquals(RiskLevel.CRITICAL, response.effectiveRiskLevel(), "Effective risk level must be overridden to CRITICAL");
        assertNotNull(response.activeDisasterAlert());
        assertEquals("NDMA-2026-CYC-0042", response.activeDisasterAlert().identifier());
        assertTrue(response.recommendation().contains("CIVIL DEFENSE DISASTER OVERRIDE"));

        // Baseline score and factors must be PRESERVED without alteration
        assertEquals(baselineScore, response.safetyScore(), "Underlying safety score must be mathematically identical");
        assertEquals(RiskLevel.LOW, response.riskLevel(), "Underlying risk level must remain LOW");
        assertEquals(contributions, response.contributingFactors(), "Contributing factors must remain intact");

        // Verify locationService was NOT called a second time (P18)
        verify(locationService, never()).getCurrentLocation(userId);
    }

    @Test
    @DisplayName("Moderate alert does not trigger emergency override and does not elevate baseline risk level")
    void moderateAlertRemainsAdvisoryWithoutOverride() {
        BigDecimal baselineScore = new BigDecimal("25.00");
        RiskLevel baselineLevel = RiskLevel.LOW;
        BaselineRiskResult baselineResult = new BaselineRiskResult(
                baselineScore, baselineLevel, List.of(),
                "Normal baseline recommendation", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(19.81), BigDecimal.valueOf(85.83),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        SachetAlertSummary moderateAlert = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-2026-MOD-0010", "imd_alert@sachet.ndma.gov.in",
                Instant.now(), "Met", "Heavy Rain Advisory", "Immediate", "Moderate", "Observed",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS),
                "Heavy Rain", "Localized waterlogging possible", "Drive with caution",
                "Coastal Puri", false
        );

        when(sachetAlertService.findApplicableActiveAlert(anyDouble(), anyDouble(), any(Instant.class)))
                .thenReturn(Optional.of(moderateAlert));
        when(sachetAlertService.isQualifyingSevereAlert(moderateAlert)).thenReturn(false);

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertFalse(response.overrideActive(), "Moderate alert must NOT trigger emergency override");
        assertEquals(RiskLevel.LOW, response.effectiveRiskLevel(), "Effective risk level must remain baseline LOW");
        assertEquals(baselineScore, response.safetyScore());
        assertEquals(baselineLevel, response.riskLevel());
        assertNotNull(response.activeDisasterAlert(), "Moderate alert is attached as informational advisory");
    }

    @Test
    @DisplayName("No active alert results in overrideActive=false and effectiveRiskLevel=riskLevel")
    void noAlertNoOverride() {
        BigDecimal baselineScore = new BigDecimal("45.00");
        RiskLevel baselineLevel = RiskLevel.MEDIUM;
        BaselineRiskResult baselineResult = new BaselineRiskResult(
                baselineScore, baselineLevel, List.of(),
                "Normal baseline recommendation", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(19.81), BigDecimal.valueOf(85.83),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        when(sachetAlertService.findApplicableActiveAlert(anyDouble(), anyDouble(), any(Instant.class)))
                .thenReturn(Optional.empty());

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertFalse(response.overrideActive());
        assertEquals(RiskLevel.MEDIUM, response.effectiveRiskLevel());
        assertEquals(baselineScore, response.safetyScore());
        assertEquals(baselineLevel, response.riskLevel());
        assertNull(response.activeDisasterAlert());
    }

    @Test
    @DisplayName("Expired alert at exact boundary results in no override, preserves baseline score and riskLevel")
    void expiredAlertAtBoundaryYieldsNoOverrideAndPreservesBaseline() {
        BigDecimal baselineScore = new BigDecimal("42.50");
        RiskLevel baselineLevel = RiskLevel.LOW;
        BaselineRiskResult baselineResult = new BaselineRiskResult(
                baselineScore, baselineLevel, List.of(),
                "Baseline recommendation", "BASELINE_WEIGHTED", null
        );

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable(), RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable()
        );

        LocationResponse location = new LocationResponse(
                1L, BigDecimal.valueOf(28.61), BigDecimal.valueOf(77.20),
                BigDecimal.valueOf(10.0), BigDecimal.ZERO, Instant.now()
        );

        when(riskContextAssembler.assembleContextForCurrentUser(userId))
                .thenReturn(new RiskAssemblyContext(request, location));
        when(riskFusionService.calculateBaselineRisk(request)).thenReturn(baselineResult);

        when(sachetAlertService.findApplicableActiveAlert(eq(28.61), eq(77.20), any(Instant.class)))
                .thenReturn(Optional.empty());

        RiskResponse response = riskApiService.getCurrentRisk(userId);

        assertNotNull(response);
        assertFalse(response.overrideActive(), "Override must be inactive when alert is expired at boundary");
        assertEquals(RiskLevel.LOW, response.effectiveRiskLevel(), "Effective risk level must remain baseline LOW");
        assertEquals(baselineScore, response.safetyScore(), "Baseline safety score must be preserved");
        assertEquals(baselineLevel, response.riskLevel(), "Baseline risk level must be preserved");
        assertNull(response.activeDisasterAlert(), "Stale active disaster alert must NOT be present");
    }
}
