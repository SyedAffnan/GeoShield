package com.geoshield.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.config.RiskFusionProperties;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.dto.BaselineRiskCalculationRequest;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskAssemblyContext;
import com.geoshield.risk.dto.RiskFactorInput;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.repository.RiskScoreRepository;
import com.geoshield.risk.service.BaselineRiskFusionService;
import com.geoshield.risk.service.EmergencyServiceProximityRiskService;
import com.geoshield.risk.service.GeographicResolutionService;
import com.geoshield.risk.service.HistoricalRiskFeatureService;
import com.geoshield.risk.service.IncidentRiskFeatureService;
import com.geoshield.risk.service.RiskApiServiceImpl;
import com.geoshield.risk.service.RiskContextAssembler;
import com.geoshield.risk.service.TimeOfDayRiskService;
import com.geoshield.risk.service.WeatherRiskService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * In-process algorithmic microbenchmark measuring service-level baseline calculation,
 * context assembly, and internal service pipeline execution.
 *
 * <p><strong>Scope Note:</strong> This is a JVM in-process/service-level microbenchmark.
 * It measures internal CPU computation, arithmetic normalization, weighting, and object assembly.
 * It does NOT measure external HTTP transport, Spring Security filter chain overhead,
 * network latency, live Open-Meteo weather API calls, or physical database disk I/O.
 */
public class RiskEngineBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 500;

    private BaselineRiskFusionService fusionService;
    private RiskContextAssembler contextAssembler;
    private RiskApiServiceImpl riskApiService;
    private UUID userId;
    private BaselineRiskCalculationRequest sampleRequest;

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

        RiskScoreRepository riskScoreRepository = mock(RiskScoreRepository.class);
        when(riskScoreRepository.save(any(RiskScore.class))).thenAnswer(inv -> inv.getArgument(0));

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        fusionService = new BaselineRiskFusionService(properties, identityService, riskScoreRepository, objectMapper);

        LocationService locationService = mock(LocationService.class);
        when(locationService.getCurrentLocation(userId)).thenReturn(
                new LocationResponse(1L, new BigDecimal("12.9716"), new BigDecimal("77.5946"), null, null, Instant.now()));

        IncidentService incidentService = mock(IncidentService.class);
        List<IncidentResponse> incidents = List.of(
                new IncidentResponse(UUID.randomUUID(), "Road hazard", "Debris on road",
                        new BigDecimal("12.9720"), new BigDecimal("77.5950"), "REPORTED", "a".repeat(64),
                        IncidentSourceType.USER_REPORTED, Instant.now()));
        when(incidentService.getActiveIncidents()).thenReturn(incidents);

        GeographicResolutionService geographicResolutionService = mock(GeographicResolutionService.class);
        GeographicResolution resolution = GeographicResolution.resolved(GeographicLevel.STATE_UT, "Karnataka");
        when(geographicResolutionService.resolve(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(resolution);

        HistoricalRiskFeatureService historicalRiskFeatureService = mock(HistoricalRiskFeatureService.class);
        when(historicalRiskFeatureService.historicalIncidentRisk(any(GeographicResolution.class))).thenReturn(
                new NormalizedRiskFeature(RiskFactorType.HISTORICAL_INCIDENT, new BigDecimal("50.55"), true,
                        "MoRTH 2024", "State/UT-level metric", "metric / max * 100", null, "77.2 per lakh",
                        "HISTORICAL", "MoRTH-2024-STATE-UT", null, null, "STATE_UT: Karnataka", "detail"));

        IncidentRiskFeatureService incidentRiskFeatureService = mock(IncidentRiskFeatureService.class);
        when(incidentRiskFeatureService.userReportRisk(any(), any(), any())).thenReturn(
                new NormalizedRiskFeature(RiskFactorType.USER_REPORT, new BigDecimal("20.00"), true,
                        "GeoShield Incidents", "1 nearby incident", "count-based", null, "1 active incident",
                        "USER_INCIDENTS", "INCIDENT-CLUSTER-1", Instant.now(), 300L, "RADIUS_10KM", "detail"));

        TimeOfDayRiskService timeOfDayRiskService = mock(TimeOfDayRiskService.class);
        when(timeOfDayRiskService.currentRisk()).thenReturn(
                new NormalizedRiskFeature(RiskFactorType.TIME_OF_DAY, new BigDecimal("40.00"), true,
                        "MoRTH Table 7.3", "Daytime distribution", "time-slot share", null, "15:00-18:00 (18.2%)",
                        "TEMPORAL_RULE", "MoRTH-2024-Table-7.3", null, null, "NATIONAL", "detail"));

        WeatherRiskService weatherRiskService = mock(WeatherRiskService.class);
        when(weatherRiskService.currentRisk(any(), any())).thenReturn(
                new NormalizedRiskFeature(RiskFactorType.WEATHER, new BigDecimal("35.00"), true,
                        "Open-Meteo", "Light rain", "severity mapping", null, "WMO 61 (Light Rain)",
                        "EXTERNAL_API", "Open-Meteo-WMO-61", Instant.now(), 600L, "GRID_0.01DEG", "detail"));

        EmergencyServiceProximityRiskService proximityService = mock(EmergencyServiceProximityRiskService.class);
        when(proximityService.proximityRisk(any(), any())).thenReturn(
                new NormalizedRiskFeature(RiskFactorType.SERVICE_PROXIMITY, new BigDecimal("15.00"), true,
                        "OpenStreetMap", "Nearest hospital 1.2km", "distance decay", null, "1.2 km to Hospital",
                        "FACILITY_REGISTRY", "OSM-NODE-123", null, null, "RADIUS_10KM", "detail"));

        contextAssembler = new RiskContextAssembler(locationService, incidentService, geographicResolutionService,
                historicalRiskFeatureService, incidentRiskFeatureService, timeOfDayRiskService,
                weatherRiskService, proximityService);

        SachetAlertService sachetAlertService = mock(SachetAlertService.class);
        when(sachetAlertService.findApplicableActiveAlert(any(Double.class), any(Double.class), any(Instant.class))).thenReturn(Optional.empty());

        riskApiService = new RiskApiServiceImpl(contextAssembler, fusionService, locationService,
                sachetAlertService, riskScoreRepository, identityService, objectMapper);

        sampleRequest = contextAssembler.assembleForCurrentUser(userId);
    }

    @Test
    @DisplayName("Step 6D: Benchmark Risk Engine Service-Level Algorithmic Performance and Numerical Invariance")
    void benchmarkRiskEnginePerformanceAndInvariance() {
        // 1. Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            fusionService.calculateBaselineRisk(sampleRequest);
            contextAssembler.assembleContextForCurrentUser(userId);
            riskApiService.getCurrentRisk(userId);
        }

        // 2. Measure Baseline Risk Calculation Latency
        List<Long> baselineNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        BaselineRiskResult referenceBaselineResult = null;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            BaselineRiskResult result = fusionService.calculateBaselineRisk(sampleRequest);
            long elapsed = System.nanoTime() - start;
            baselineNanos.add(elapsed);

            if (referenceBaselineResult == null) {
                referenceBaselineResult = result;
            } else {
                assertEquals(0, referenceBaselineResult.score().compareTo(result.score()), "Score invariance failure");
                assertEquals(referenceBaselineResult.riskLevel(), result.riskLevel(), "RiskLevel invariance failure");
                assertEquals(referenceBaselineResult.contributingFactors().size(), result.contributingFactors().size());
            }
        }

        // 3. Measure Context Assembly Latency
        List<Long> assemblyNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskAssemblyContext ctx = contextAssembler.assembleContextForCurrentUser(userId);
            long elapsed = System.nanoTime() - start;
            assemblyNanos.add(elapsed);
        }

        // 4. Measure In-Process Service-Level Risk Decision Pipeline Latency
        List<Long> decisionPipelineNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        RiskResponse referenceResponse = null;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            RiskResponse resp = riskApiService.getCurrentRisk(userId);
            long elapsed = System.nanoTime() - start;
            decisionPipelineNanos.add(elapsed);

            if (referenceResponse == null) {
                referenceResponse = resp;
            } else {
                assertEquals(0, referenceResponse.safetyScore().compareTo(resp.safetyScore()), "Service-level SafetyScore invariance failure");
                assertEquals(referenceResponse.riskLevel(), resp.riskLevel(), "Service-level RiskLevel invariance failure");
            }
        }

        // Output Benchmark Statistics
        printStats("1. Baseline Risk Calculation (Algorithmic Fusion)", baselineNanos);
        printStats("2. Risk-Context Assembly (In-Memory Feature Assembly)", assemblyNanos);
        printStats("3. Service-Level Risk Decision Pipeline (In-Process)", decisionPipelineNanos);
    }

    /**
     * Prints statistical summary for the sample.
     * Percentile convention: floor-index percentile convention (index = (int)(n * percentile))
     * on the sorted sample of size n=500 (e.g., p95 at index 475, p99 at index 495).
     */
    private void printStats(String name, List<Long> nanosList) {
        Collections.sort(nanosList);
        int n = nanosList.size();
        long min = nanosList.get(0);
        long max = nanosList.get(n - 1);
        double avg = nanosList.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long median = nanosList.get(n / 2);
        long p95 = nanosList.get((int) (n * 0.95));
        long p99 = nanosList.get((int) (n * 0.99));

        System.out.printf("BENCHMARK [%s] - Iterations: %d (Floor Index: p95=%d, p99=%d)%n",
                name, n, (int) (n * 0.95), (int) (n * 0.99));
        System.out.printf("  Min:    %.3f ms (%d ns)%n", min / 1_000_000.0, min);
        System.out.printf("  Max:    %.3f ms (%d ns)%n", max / 1_000_000.0, max);
        System.out.printf("  Avg:    %.3f ms (%.1f ns)%n", avg / 1_000_000.0, avg);
        System.out.printf("  Median: %.3f ms (%d ns)%n", median / 1_000_000.0, median);
        System.out.printf("  p95:    %.3f ms (%d ns)%n", p95 / 1_000_000.0, p95);
        System.out.printf("  p99:    %.3f ms (%d ns)%n", p99 / 1_000_000.0, p99);
    }
}
