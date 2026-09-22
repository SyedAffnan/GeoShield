package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskDataCompleteness;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorDetail;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskDataCompletenessTest {

    @Mock private IdentityService identityService;
    @Mock private RiskScoreRepository riskScoreRepository;
    @Mock private User user;

    private BaselineRiskFusionService service;
    private ObjectMapper objectMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new BaselineRiskFusionService(properties(), identityService, riskScoreRepository, objectMapper);
        lenient().when(identityService.getUserById(userId)).thenReturn(user);
    }

    @Test
    void allFiveLiveFactorsAvailableReportsFullCompletenessAndUnchangedScore() {
        // Historical (40 * 0.30 = 12.0), Weather (50 * 0.20 = 10.0), Time (60 * 0.15 = 9.0),
        // Proximity (20 * 0.15 = 3.0), User Report (10 * 0.10 = 1.0) -> sum = 35.0 (LOW)
        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(BigDecimal.valueOf(40), "MoRTH rate 40", "MoRTH Road Accidents in India 2024"),
                RiskFactorInput.available(BigDecimal.valueOf(50), "WMO code 0", "Open-Meteo"),
                RiskFactorInput.available(BigDecimal.valueOf(60), "12:00-15:00", "MoRTH Table 7.3"),
                RiskFactorInput.available(BigDecimal.valueOf(20), "2.0 km to Hospital", "OpenStreetMap Emergency Amenities (ODbL)"),
                RiskFactorInput.available(BigDecimal.valueOf(10), "1 active incident in 10 km", "GeoShield Incident Reports"),
                RiskFactorInput.unavailable("Connectivity is dormant", "CONNECTIVITY_DORMANT", "Client"),
                RiskFactorInput.unavailable("Other context is dormant", "OTHER_CONTEXT_DORMANT", "Environment"));

        BaselineRiskResult result = service.calculateBaselineRisk(request);

        assertEquals(0, result.score().compareTo(new BigDecimal("35.00")));
        assertEquals(RiskLevel.LOW, result.riskLevel());

        RiskDataCompleteness completeness = result.dataCompleteness();
        assertNotNull(completeness);
        assertEquals(5, completeness.availableFactorCount());
        assertEquals(5, completeness.expectedLiveFactorCount());
        assertEquals(0, completeness.availabilityRatio().compareTo(new BigDecimal("1.0")));
        assertFalse(completeness.degraded());
        assertTrue(completeness.missingFactors().isEmpty());
        assertTrue(completeness.missingReasons().isEmpty());

        List<RiskFactorDetail> details = result.factorDetails();
        assertEquals(5, details.size());
        assertTrue(details.stream().allMatch(RiskFactorDetail::available));
        assertTrue(details.stream().allMatch(d -> d.reason() == null));
        assertEquals(0, details.get(0).weightedContribution().compareTo(new BigDecimal("12.00")));
        assertEquals(0, details.get(1).weightedContribution().compareTo(new BigDecimal("10.00")));
    }

    @Test
    void oneLiveFactorUnavailableReportsFourFifthsCompletenessAndZeroContribution() {
        // Weather unavailable. Others available.
        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(BigDecimal.valueOf(40)),
                RiskFactorInput.unavailable("Open-Meteo timeout", "WEATHER_PROVIDER_UNAVAILABLE", "Open-Meteo"),
                RiskFactorInput.available(BigDecimal.valueOf(60)),
                RiskFactorInput.available(BigDecimal.valueOf(20)),
                RiskFactorInput.available(BigDecimal.valueOf(10)),
                RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable());

        BaselineRiskResult result = service.calculateBaselineRisk(request);

        // Historical 12.0 + Weather 0.0 + Time 9.0 + Proximity 3.0 + User 1.0 = 25.0
        assertEquals(0, result.score().compareTo(new BigDecimal("25.00")));

        RiskDataCompleteness completeness = result.dataCompleteness();
        assertEquals(4, completeness.availableFactorCount());
        assertEquals(5, completeness.expectedLiveFactorCount());
        assertEquals(0, completeness.availabilityRatio().compareTo(new BigDecimal("0.8")));
        assertTrue(completeness.degraded());
        assertEquals(List.of(RiskFactorType.WEATHER), completeness.missingFactors());
        assertEquals("WEATHER_PROVIDER_UNAVAILABLE", completeness.missingReasons().get(RiskFactorType.WEATHER));

        // Remaining weights are NOT redistributed
        RiskFactorDetail weatherDetail = result.factorDetails().stream()
                .filter(d -> d.factor() == RiskFactorType.WEATHER)
                .findFirst().orElseThrow();
        assertFalse(weatherDetail.available());
        assertEquals(0, weatherDetail.weight().compareTo(new BigDecimal("0.20")));
        assertEquals(BigDecimal.ZERO, weatherDetail.normalizedValue());
        assertEquals(BigDecimal.ZERO, weatherDetail.weightedContribution());
        assertEquals("WEATHER_PROVIDER_UNAVAILABLE", weatherDetail.reason());
        assertTrue(weatherDetail.explanation().contains("Open-Meteo timeout"));

        // Verify dormant factors are NOT in missingFactors
        assertFalse(completeness.missingFactors().contains(RiskFactorType.CONNECTIVITY));
        assertFalse(completeness.missingFactors().contains(RiskFactorType.OTHER_CONTEXT));
    }

    @Test
    void multipleLiveFactorsUnavailableReportsAccurateCountAndReasons() {
        // Weather and Emergency Proximity unavailable
        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(BigDecimal.valueOf(40)),
                RiskFactorInput.unavailable("Open-Meteo timeout", "WEATHER_PROVIDER_UNAVAILABLE", "Open-Meteo"),
                RiskFactorInput.available(BigDecimal.valueOf(60)),
                RiskFactorInput.unavailable("No facilities in DB", "EMERGENCY_FACILITY_DATA_UNAVAILABLE", "OpenStreetMap"),
                RiskFactorInput.available(BigDecimal.valueOf(10)),
                RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable());

        BaselineRiskResult result = service.calculateBaselineRisk(request);

        // Historical 12.0 + Time 9.0 + User 1.0 = 22.0
        assertEquals(0, result.score().compareTo(new BigDecimal("22.00")));

        RiskDataCompleteness completeness = result.dataCompleteness();
        assertEquals(3, completeness.availableFactorCount());
        assertEquals(5, completeness.expectedLiveFactorCount());
        assertEquals(0, completeness.availabilityRatio().compareTo(new BigDecimal("0.6")));
        assertTrue(completeness.degraded());
        assertEquals(2, completeness.missingFactors().size());
        assertTrue(completeness.missingFactors().contains(RiskFactorType.WEATHER));
        assertTrue(completeness.missingFactors().contains(RiskFactorType.SERVICE_PROXIMITY));
        assertEquals("WEATHER_PROVIDER_UNAVAILABLE", completeness.missingReasons().get(RiskFactorType.WEATHER));
        assertEquals("EMERGENCY_FACILITY_DATA_UNAVAILABLE", completeness.missingReasons().get(RiskFactorType.SERVICE_PROXIMITY));
    }

    @Test
    void allFiveLiveFactorsUnavailablePreservesExistingScoreZeroAndLowClassification() {
        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.unavailable("No state metric", "STATE_METRIC_NOT_FOUND", "MoRTH"),
                RiskFactorInput.unavailable("Provider down", "WEATHER_PROVIDER_UNAVAILABLE", "Open-Meteo"),
                RiskFactorInput.unavailable("Distribution unmapped", "TIME_INTERVAL_UNMAPPED", "MoRTH Table 7.3"),
                RiskFactorInput.unavailable("No facilities", "EMERGENCY_FACILITY_DATA_UNAVAILABLE", "OpenStreetMap"),
                RiskFactorInput.unavailable("Incident service down", "INCIDENT_DATA_UNAVAILABLE", "GeoShield"),
                RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable());

        BaselineRiskResult result = service.calculateBaselineRisk(request);

        // Score 0.0, Level LOW (proven existing behavior)
        assertEquals(0, result.score().compareTo(BigDecimal.ZERO));
        assertEquals(RiskLevel.LOW, result.riskLevel());
        assertEquals("Normal precautions are recommended.", result.recommendation());

        RiskDataCompleteness completeness = result.dataCompleteness();
        assertEquals(0, completeness.availableFactorCount());
        assertEquals(5, completeness.expectedLiveFactorCount());
        assertEquals(0, completeness.availabilityRatio().compareTo(BigDecimal.ZERO));
        assertTrue(completeness.degraded());
        assertEquals(5, completeness.missingFactors().size());
        assertEquals(5, completeness.missingReasons().size());
    }

    @Test
    void zeroIncidentsEvaluatesAsAvailableWithZeroScoreNotUnavailable() {
        // Active incident count = 0 is a valid measurement (0 incident risk), available: true
        RiskFactorInput userReportInput = RiskFactorInput.available(
                BigDecimal.ZERO,
                "0 active incidents in 10 km",
                "GeoShield Incident Reports");

        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(BigDecimal.valueOf(40)),
                RiskFactorInput.available(BigDecimal.valueOf(50)),
                RiskFactorInput.available(BigDecimal.valueOf(60)),
                RiskFactorInput.available(BigDecimal.valueOf(20)),
                userReportInput,
                RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable());

        BaselineRiskResult result = service.calculateBaselineRisk(request);

        RiskDataCompleteness completeness = result.dataCompleteness();
        assertEquals(5, completeness.availableFactorCount());
        assertFalse(completeness.degraded());
        assertFalse(completeness.missingFactors().contains(RiskFactorType.USER_REPORT));

        RiskFactorDetail userReportDetail = result.factorDetails().stream()
                .filter(d -> d.factor() == RiskFactorType.USER_REPORT)
                .findFirst().orElseThrow();
        assertTrue(userReportDetail.available());
        assertEquals(BigDecimal.ZERO, userReportDetail.normalizedValue());
        assertEquals(0, userReportDetail.weightedContribution().compareTo(BigDecimal.ZERO));
        assertNull(userReportDetail.reason());
    }

    @Test
    void persistenceStrictlyPreservesJsonArrayRootInContributingFactorsColumn() throws Exception {
        BaselineRiskCalculationRequest request = new BaselineRiskCalculationRequest(
                userId,
                RiskFactorInput.available(BigDecimal.valueOf(40), "rate 40", "MoRTH"),
                RiskFactorInput.unavailable("Weather down", "WEATHER_PROVIDER_UNAVAILABLE", "Open-Meteo"),
                RiskFactorInput.available(BigDecimal.valueOf(60)),
                RiskFactorInput.available(BigDecimal.valueOf(20)),
                RiskFactorInput.available(BigDecimal.valueOf(10)),
                RiskFactorInput.unavailable(),
                RiskFactorInput.unavailable());

        service.calculateBaselineRisk(request);

        ArgumentCaptor<RiskScore> captor = ArgumentCaptor.forClass(RiskScore.class);
        verify(riskScoreRepository).save(captor.capture());

        RiskScore savedScore = captor.getValue();
        assertNotNull(savedScore);
        String json = savedScore.getContributingFactors();
        assertNotNull(json);

        // MUST be a JSON array root
        JsonNode rootNode = objectMapper.readTree(json);
        assertTrue(rootNode.isArray(), "The contributing_factors root MUST remain a JSON array");

        // Verify deserialization into List<RiskFactorContribution> works without error
        List<RiskFactorContribution> deserialized = objectMapper.readValue(
                json, new TypeReference<List<RiskFactorContribution>>() {});
        assertEquals(7, deserialized.size());

        // Verify additive fields are present in the JSON elements
        RiskFactorContribution weather = deserialized.stream()
                .filter(c -> c.factor() == RiskFactorType.WEATHER)
                .findFirst().orElseThrow();
        assertFalse(weather.available());
        assertEquals("WEATHER_PROVIDER_UNAVAILABLE", weather.reasonCode());
    }

    @Test
    void riskResponseSerializesAdditivelyWithCompletenessAndFactorDetails() {
        RiskDataCompleteness completeness = RiskDataCompleteness.of(
                4,
                List.of(RiskFactorType.WEATHER),
                java.util.Map.of(RiskFactorType.WEATHER, "WEATHER_PROVIDER_UNAVAILABLE"));

        List<RiskFactorContribution> contributions = List.of(
                new RiskFactorContribution(RiskFactorType.HISTORICAL_INCIDENT, true, BigDecimal.valueOf(40),
                        new BigDecimal("0.30"), new BigDecimal("12.00"), "Historical contributed 12 risk points."));

        List<RiskFactorDetail> details = List.of(
                new RiskFactorDetail(RiskFactorType.HISTORICAL_INCIDENT, new BigDecimal("0.30"), true,
                        "rate 40", BigDecimal.valueOf(40), new BigDecimal("12.00"), null, "Historical ok", "MoRTH"));

        RiskResponse response = new RiskResponse(
                new BigDecimal("12.00"),
                RiskLevel.LOW,
                "Normal precautions are recommended.",
                contributions,
                completeness,
                details,
                "BASELINE_WEIGHTED",
                null);

        assertEquals(new BigDecimal("12.00"), response.safetyScore());
        assertEquals(RiskLevel.LOW, response.riskLevel());
        assertEquals(1, response.contributingFactors().size());
        assertEquals(completeness, response.dataCompleteness());
        assertEquals(details, response.factorDetails());
    }

    private RiskFusionProperties properties() {
        return new RiskFusionProperties(
                new BigDecimal("0.30"),
                new BigDecimal("0.20"),
                new BigDecimal("0.15"),
                new BigDecimal("0.15"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.05"),
                39, 59, 79);
    }
}
