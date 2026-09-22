package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorDetail;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies mathematical scoring invariance: adding provenance metadata to inputs has zero
 * numerical drift or behavioral side effects on baseline risk scoring.
 *
 * <p>Directly executes a differential comparison between identical inputs with and without
 * provenance metadata through the same production scoring pipeline.
 */
class RiskScoringInvarianceTest {

    private BaselineRiskFusionService fusionService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        RiskFusionProperties properties = new RiskFusionProperties(
                new BigDecimal("0.30"),
                new BigDecimal("0.20"),
                new BigDecimal("0.15"),
                new BigDecimal("0.15"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.05"),
                39, 59, 79
        );
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.getUserById(userId)).thenReturn(mock(User.class));
        RiskScoreRepository repository = mock(RiskScoreRepository.class);
        when(repository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        fusionService = new BaselineRiskFusionService(properties, identityService, repository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("Differential Invariance: Provenance-free vs Provenance-bearing inputs produce strictly identical scores, weights, and contributions")
    void differentialInvarianceBetweenProvenanceFreeAndProvenanceBearing() {
        // --- Set A: Provenance-free inputs (legacy factory without provenance fields) ---
        RiskFactorInput histA = RiskFactorInput.available(new BigDecimal("50.0"), "77.2 per lakh", "MoRTH 2024");
        RiskFactorInput weatherA = RiskFactorInput.available(new BigDecimal("70.0"), "WMO 61 (Rainy)", "Open-Meteo");
        RiskFactorInput timeOfDayA = RiskFactorInput.available(new BigDecimal("40.0"), "15:00-18:00 (18.2%)", "MoRTH Table 7.3");
        RiskFactorInput proximityA = RiskFactorInput.available(new BigDecimal("20.0"), "2.00 km to Hospital", "OSM");
        RiskFactorInput reportsA = RiskFactorInput.available(new BigDecimal("60.0"), "2 active incidents", "GeoShield Incidents");
        RiskFactorInput connA = RiskFactorInput.unavailable("No client connectivity input contract is configured.");
        RiskFactorInput otherA = RiskFactorInput.unavailable("No other approved contextual signal is available.");

        // --- Set B: Identical numeric values with full Step 4 provenance attached ---
        Instant now = Instant.parse("2026-08-24T12:00:00Z");
        RiskFactorInput histB = RiskFactorInput.available(
                new BigDecimal("50.0"), "77.2 per lakh", "MoRTH 2024",
                "HISTORICAL", "MoRTH-2024-STATE-UT", null, null, "STATE_UT: Karnataka", "metric / max * 100"
        );
        RiskFactorInput weatherB = RiskFactorInput.available(
                new BigDecimal("70.0"), "WMO 61 (Rainy)", "Open-Meteo",
                "EXTERNAL_API", "Open-Meteo-WMO-61", now.minusSeconds(900), 900L,
                "LOCATION_GRID_0.01DEG[12.97,77.59]", "Table 3.8 mapping"
        );
        RiskFactorInput timeOfDayB = RiskFactorInput.available(
                new BigDecimal("40.0"), "15:00-18:00 (18.2%)", "MoRTH Table 7.3",
                "TEMPORAL_RULE", "MoRTH-2024-Table-7.3-15:00-18:00", null, null,
                "NATIONAL", "Interval distribution share"
        );
        RiskFactorInput proximityB = RiskFactorInput.available(
                new BigDecimal("20.0"), "2.00 km to Hospital", "OSM",
                "FACILITY_REGISTRY", "ESC-42", null, null,
                "RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]", "distance / 10.0 * 100"
        );
        RiskFactorInput reportsB = RiskFactorInput.available(
                new BigDecimal("60.0"), "2 active incidents", "GeoShield Incidents",
                "USER_INCIDENTS", "QUERY(radius=10.0km,window=24.0h,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=2,ids=[...])",
                now.minusSeconds(120), 120L, "RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]", "recency and distance decayed"
        );
        RiskFactorInput connB = RiskFactorInput.unavailable("No client connectivity input contract is configured.", "CONNECTIVITY_DORMANT", "Client connectivity");
        RiskFactorInput otherB = RiskFactorInput.unavailable("No other approved contextual signal is available.", "OTHER_CONTEXT_DORMANT", "Other context");

        BaselineRiskCalculationRequest requestA = new BaselineRiskCalculationRequest(
                userId, histA, weatherA, timeOfDayA, proximityA, reportsA, connA, otherA
        );
        BaselineRiskCalculationRequest requestB = new BaselineRiskCalculationRequest(
                userId, histB, weatherB, timeOfDayB, proximityB, reportsB, connB, otherB
        );

        // Execute both through the exact same production scoring service
        BaselineRiskResult resultA = fusionService.calculateBaselineRisk(requestA);
        BaselineRiskResult resultB = fusionService.calculateBaselineRisk(requestB);

        // 1. Assert identical overall decision metrics
        assertEquals(resultA.score(), resultB.score(), "Overall decimal score must be strictly identical");
        assertEquals(resultA.riskLevel(), resultB.riskLevel(), "RiskLevel classification must be strictly identical");
        assertEquals(resultA.recommendation(), resultB.recommendation(), "Recommendation text must be strictly identical");

        // 2. Assert identical factor contributions
        Map<RiskFactorType, RiskFactorContribution> contribsA = resultA.contributingFactors().stream()
                .collect(Collectors.toMap(RiskFactorContribution::factor, c -> c));
        Map<RiskFactorType, RiskFactorContribution> contribsB = resultB.contributingFactors().stream()
                .collect(Collectors.toMap(RiskFactorContribution::factor, c -> c));

        assertEquals(contribsA.keySet(), contribsB.keySet());
        for (RiskFactorType factor : contribsA.keySet()) {
            RiskFactorContribution ca = contribsA.get(factor);
            RiskFactorContribution cb = contribsB.get(factor);

            assertEquals(ca.available(), cb.available(), "Availability must match for " + factor);
            assertEquals(ca.weight(), cb.weight(), "Weight must match for " + factor);
            assertEquals(ca.normalizedRisk(), cb.normalizedRisk(), "Normalized risk value must match for " + factor);
            assertEquals(ca.contribution(), cb.contribution(), "Weighted contribution must match for " + factor);
        }

        // 3. Assert identical factor details core scoring fields
        Map<RiskFactorType, RiskFactorDetail> detailsA = resultA.factorDetails().stream()
                .collect(Collectors.toMap(RiskFactorDetail::factor, d -> d));
        Map<RiskFactorType, RiskFactorDetail> detailsB = resultB.factorDetails().stream()
                .collect(Collectors.toMap(RiskFactorDetail::factor, d -> d));

        assertEquals(detailsA.keySet(), detailsB.keySet());
        for (RiskFactorType factor : detailsA.keySet()) {
            RiskFactorDetail da = detailsA.get(factor);
            RiskFactorDetail db = detailsB.get(factor);

            assertEquals(da.normalizedValue(), db.normalizedValue(), "Normalized value must match for " + factor);
            assertEquals(da.weight(), db.weight(), "Weight must match for " + factor);
            assertEquals(da.weightedContribution(), db.weightedContribution(), "Contribution must match for " + factor);
            assertEquals(da.available(), db.available(), "Availability must match for " + factor);

            // Verify A has null provenance while B has populated provenance
            assertNull(da.sourceType(), "Set A must have null sourceType");
            assertNotNull(db.sourceType(), "Set B must have non-null sourceType for " + factor);
        }
    }

    @Test
    @DisplayName("Invariance: Missing factors contribute 0.0 with no renormalization and max baseline 90.0")
    void missingFactorsContributeZeroWithoutRenormalization() {
        // All live factors at maximum (100.0)
        RiskFactorInput maxFactor = RiskFactorInput.available(BigDecimal.valueOf(100.0));
        RiskFactorInput conn = RiskFactorInput.unavailable("Dormant", "CONNECTIVITY_DORMANT", "Conn");
        RiskFactorInput other = RiskFactorInput.unavailable("Dormant", "OTHER_CONTEXT_DORMANT", "Other");

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId, maxFactor, maxFactor, maxFactor, maxFactor, maxFactor, conn, other
        );

        BaselineRiskResult result = fusionService.calculateBaselineRisk(request);

        // 100 * (0.30 + 0.20 + 0.15 + 0.15 + 0.10) = 90.00
        assertEquals(0, new BigDecimal("90.00").compareTo(result.score()));
        assertEquals(RiskLevel.CRITICAL, result.riskLevel()); // 90 >= 80 -> CRITICAL
    }
}
