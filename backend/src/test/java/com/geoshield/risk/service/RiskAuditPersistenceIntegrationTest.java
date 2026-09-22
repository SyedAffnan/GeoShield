package com.geoshield.risk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.RoleRepository;
import com.geoshield.identity.repository.UserRepository;
import com.geoshield.risk.dto.RiskFactorDetail;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.entity.RiskScore;
import com.geoshield.risk.entity.RiskScoringMethod;
import com.geoshield.risk.repository.RiskScoreRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real MySQL database integration test verifying RiskScore audit persistence,
 * native JSON column mapping (factor_details, contributing_factors, data_completeness),
 * Step 4 provenance metadata round-tripping, and legacy compatibility.
 *
 * <p>Tagged with {@code @Tag("integration")} and enabled conditionally via
 * {@code @EnabledIfEnvironmentVariable(named = "GEOSHIELD_DB_PASSWORD", matches = ".+")}.
 * Crucially, this does NOT use Mockito to fake persistence; it runs against the live target
 * MySQL database to verify that:
 * <ul>
 *   <li>RiskScore entity persists into MySQL's native JSON columns;</li>
 *   <li>All 6 Step 4 provenance fields serialize and deserialize correctly;</li>
 *   <li>Instant timestamps round-trip without corruption or timezone distortion;</li>
 *   <li>Null fields serialize faithfully without breaking schema validation;</li>
 *   <li>Payload fits the actual database column definition;</li>
 *   <li>Legacy factor_details payloads remain readable without regression.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest(properties = {
        "geoshield.jwt.secret=integration-test-secret-at-least-32-bytes-long-123456"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GEOSHIELD_DB_PASSWORD", matches = ".+")
class RiskAuditPersistenceIntegrationTest {

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testTourist;
    private final List<UUID> createdDecisionIds = new ArrayList<>();

    private UserRole getOrCreateTouristRole() {
        return roleRepository.findByName(Role.TOURIST)
                .orElseGet(() -> roleRepository.save(new UserRole(Role.TOURIST)));
    }

    @BeforeEach
    void setUp() {
        testTourist = new User(
                "risk_audit_user_" + UUID.randomUUID().toString().substring(0, 8),
                "risk_audit_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hash",
                "Risk Audit Tourist",
                "+919876543200",
                getOrCreateTouristRole()
        );
        testTourist = userRepository.saveAndFlush(testTourist);
    }

    @AfterEach
    void tearDown() {
        for (UUID decisionId : createdDecisionIds) {
            riskScoreRepository.findByDecisionId(decisionId).ifPresent(riskScoreRepository::delete);
        }
        createdDecisionIds.clear();

        if (testTourist != null && testTourist.getId() != null) {
            userRepository.delete(testTourist);
            testTourist = null;
        }
    }

    @Test
    @DisplayName("Real MySQL: factor_details JSON column stores all 6 provenance fields and round-trips correctly")
    void realMysql_persistsProvenanceJsonInFactorDetailsColumn_andRoundTripsAllFields() throws Exception {
        Instant weatherObservedAt = Instant.parse("2026-08-24T10:15:30Z");
        Instant incidentReportedAt = Instant.parse("2026-08-24T10:28:00Z");

        List<RiskFactorDetail> factorDetails = List.of(
                new RiskFactorDetail(
                        RiskFactorType.HISTORICAL_INCIDENT,
                        new BigDecimal("0.30"),
                        true,
                        "77.2 per lakh",
                        new BigDecimal("50.0"),
                        new BigDecimal("15.0"),
                        null,
                        "Historical incident data contributed 15.00 risk points.",
                        "MoRTH Road Accidents in India 2024",
                        "HISTORICAL",
                        "MoRTH-2024-STATE-UT",
                        null,
                        null,
                        "STATE_UT: Karnataka",
                        "MoRTH 2024 min-max normalized across State/UT benchmarks"
                ),
                new RiskFactorDetail(
                        RiskFactorType.WEATHER,
                        new BigDecimal("0.20"),
                        true,
                        "WMO 61 (Rainy)",
                        new BigDecimal("79.06699039"),
                        new BigDecimal("15.81339808"),
                        null,
                        "Weather data contributed 15.81 risk points.",
                        "Open-Meteo current weather observation; risk mapping from MoRTH Road Accidents in India 2024, Table 3.8",
                        "EXTERNAL_API",
                        "Open-Meteo-WMO-61",
                        weatherObservedAt,
                        900L,
                        "LOCATION_GRID_0.01DEG[12.97,77.59]",
                        "MoRTH Table 3.8 condition severity: 44.51 persons killed per 100 accidents"
                ),
                new RiskFactorDetail(
                        RiskFactorType.TIME_OF_DAY,
                        new BigDecimal("0.15"),
                        true,
                        "15:00-18:00 (18.2% accident share)",
                        new BigDecimal("82.61659718"),
                        new BigDecimal("12.39248958"),
                        null,
                        "Time-of-day data contributed 12.39 risk points.",
                        "MoRTH Road Accidents in India 2024, Table 7.3 (national 3-hour accident distribution in Indian local time)",
                        "TEMPORAL_RULE",
                        "MoRTH-2024-Table-7.3-15:00-18:00",
                        null,
                        null,
                        "NATIONAL",
                        "MoRTH Table 7.3 interval accident count / peak interval accident count × 100"
                ),
                new RiskFactorDetail(
                        RiskFactorType.SERVICE_PROXIMITY,
                        new BigDecimal("0.15"),
                        true,
                        "2.00 km to Hospital",
                        new BigDecimal("20.0"),
                        new BigDecimal("3.0"),
                        null,
                        "Emergency-service proximity data contributed 3.00 risk points.",
                        "OpenStreetMap Emergency Amenities (ODbL)",
                        "FACILITY_REGISTRY",
                        "ESC-42",
                        null,
                        null,
                        "RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]",
                        "Nearest emergency service facility distance / 10.0 km × 100.0, clamped to [0, 100]"
                ),
                new RiskFactorDetail(
                        RiskFactorType.USER_REPORT,
                        new BigDecimal("0.10"),
                        true,
                        "1 active incident(s), raw score 25.00",
                        new BigDecimal("25.0"),
                        new BigDecimal("2.5"),
                        null,
                        "User-report data contributed 2.50 risk points.",
                        "GeoShield Incident Reports",
                        "USER_INCIDENTS",
                        "QUERY(radius=10.0km,window=24.0h,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=1,ids=[00000000-0000-0000-0000-000000000001])",
                        incidentReportedAt,
                        150L,
                        "RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]",
                        "Sum of distance- and recency-decayed active incident severities within 10 km and 24h, clamped to [0, 100]"
                ),
                new RiskFactorDetail(
                        RiskFactorType.CONNECTIVITY,
                        new BigDecimal("0.05"),
                        false,
                        null,
                        null,
                        BigDecimal.ZERO,
                        "CONNECTIVITY_DORMANT",
                        "Connectivity data is currently unavailable.",
                        "Client connectivity",
                        "DORMANT",
                        "CONNECTIVITY_NETWORK_METRICS",
                        null,
                        null,
                        "DEVICE_LOCAL",
                        "No client connectivity input contract is configured."
                )
        );

        String factorDetailsJson = objectMapper.writeValueAsString(factorDetails);
        String contributingFactorsJson = "[{\"factor\":\"HISTORICAL_INCIDENT\",\"available\":true}]";
        String completenessJson = "{\"availableFactorCount\":5,\"totalConfiguredFactorCount\":7,\"completenessRatio\":0.7143}";

        UUID decisionId = UUID.randomUUID();
        createdDecisionIds.add(decisionId);

        RiskScore score = new RiskScore(
                decisionId,
                testTourist,
                49,
                new BigDecimal("48.71"),
                RiskLevel.MEDIUM,
                RiskLevel.MEDIUM,
                false,
                null,
                GeographicLevel.STATE_UT,
                "Karnataka",
                "29",
                completenessJson,
                factorDetailsJson,
                contributingFactorsJson,
                RiskScoringMethod.BASELINE_WEIGHTED,
                null
        );

        // Save and flush directly to real MySQL
        RiskScore saved = riskScoreRepository.saveAndFlush(score);
        assertNotNull(saved.getId(), "MySQL must generate an auto-increment primary key ID");

        // Retrieve directly from real MySQL
        RiskScore retrieved = riskScoreRepository.findByDecisionId(decisionId).orElseThrow(
                () -> new AssertionError("Must find persisted RiskScore in MySQL by decisionId")
        );

        assertEquals(decisionId, retrieved.getDecisionId());
        assertEquals(49, retrieved.getScore());
        assertEquals(new BigDecimal("48.71"), retrieved.getDecimalScore());
        assertEquals(RiskLevel.MEDIUM, retrieved.getEffectiveRiskLevel());
        assertFalse(retrieved.isOverrideActive());
        assertEquals("Karnataka", retrieved.getGeographicUnit());
        assertEquals("29", retrieved.getStateCode());

        // Validate raw factor_details column content
        String dbJson = retrieved.getFactorDetails();
        assertNotNull(dbJson);
        assertThat(dbJson).contains("EXTERNAL_API");
        assertThat(dbJson).contains("Open-Meteo-WMO-61");
        assertThat(dbJson).contains("LOCATION_GRID_0.01DEG[12.97,77.59]");
        assertThat(dbJson).contains("MoRTH-2024-STATE-UT");
        assertThat(dbJson).contains("MoRTH-2024-Table-7.3-15:00-18:00");
        assertThat(dbJson).contains("CONNECTIVITY_DORMANT");

        // Deserialize from real MySQL and verify all 6 provenance fields round-trip cleanly
        List<RiskFactorDetail> roundTripped = objectMapper.readValue(dbJson, new TypeReference<List<RiskFactorDetail>>() {});
        assertEquals(6, roundTripped.size());

        // 1. Weather: live observation timestamp, positive age, grid cell
        RiskFactorDetail weather = roundTripped.stream()
                .filter(d -> d.factor() == RiskFactorType.WEATHER).findFirst().orElseThrow();
        assertEquals("EXTERNAL_API", weather.sourceType());
        assertEquals("Open-Meteo-WMO-61", weather.sourceIdentifier());
        assertEquals(weatherObservedAt, weather.observedAt());
        assertEquals(900L, weather.freshnessSeconds());
        assertEquals("LOCATION_GRID_0.01DEG[12.97,77.59]", weather.geographicScope());
        assertNotNull(weather.normalizationDetails());

        // 2. Historical: null observedAt, null freshness, state scope
        RiskFactorDetail hist = roundTripped.stream()
                .filter(d -> d.factor() == RiskFactorType.HISTORICAL_INCIDENT).findFirst().orElseThrow();
        assertEquals("HISTORICAL", hist.sourceType());
        assertEquals("MoRTH-2024-STATE-UT", hist.sourceIdentifier());
        assertNull(hist.observedAt());
        assertNull(hist.freshnessSeconds());
        assertEquals("STATE_UT: Karnataka", hist.geographicScope());

        // 3. Time of Day: null observedAt, null freshness, national scope
        RiskFactorDetail tod = roundTripped.stream()
                .filter(d -> d.factor() == RiskFactorType.TIME_OF_DAY).findFirst().orElseThrow();
        assertEquals("TEMPORAL_RULE", tod.sourceType());
        assertEquals("MoRTH-2024-Table-7.3-15:00-18:00", tod.sourceIdentifier());
        assertNull(tod.observedAt());
        assertNull(tod.freshnessSeconds());
        assertEquals("NATIONAL", tod.geographicScope());

        // 4. Incident: live observation, positive age, radius scope
        RiskFactorDetail incident = roundTripped.stream()
                .filter(d -> d.factor() == RiskFactorType.USER_REPORT).findFirst().orElseThrow();
        assertEquals("USER_INCIDENTS", incident.sourceType());
        assertEquals(incidentReportedAt, incident.observedAt());
        assertEquals(150L, incident.freshnessSeconds());
        assertEquals("RADIUS_10KM_AROUND_LOCATION_GRID[12.97,77.59]", incident.geographicScope());

        // 5. Dormant: unavailable factor, dormant reason code, dormant provenance
        RiskFactorDetail conn = roundTripped.stream()
                .filter(d -> d.factor() == RiskFactorType.CONNECTIVITY).findFirst().orElseThrow();
        assertFalse(conn.available());
        assertEquals("CONNECTIVITY_DORMANT", conn.reason());
        assertEquals("DORMANT", conn.sourceType());
        assertEquals("CONNECTIVITY_NETWORK_METRICS", conn.sourceIdentifier());
        assertEquals("DEVICE_LOCAL", conn.geographicScope());
    }

    @Test
    @DisplayName("Real MySQL: legacy factor_details without Step 4 provenance remain fully readable without regression")
    void realMysql_persistsLegacyFactorDetails_andRemainsReadableWithoutRegression() throws Exception {
        // Legacy JSON generated prior to Step 4 provenance fields
        String legacyJson = """
                [
                  {
                    "factor": "HISTORICAL_INCIDENT",
                    "weight": 0.30,
                    "available": true,
                    "rawValue": "77.2 per lakh",
                    "normalizedValue": 50.0,
                    "weightedContribution": 15.0,
                    "reason": null,
                    "summary": "Historical incident data contributed 15.00 risk points.",
                    "source": "MoRTH Road Accidents in India 2024"
                  }
                ]
                """;

        UUID decisionId = UUID.randomUUID();
        createdDecisionIds.add(decisionId);

        RiskScore legacyScore = new RiskScore(
                decisionId,
                testTourist,
                15,
                new BigDecimal("15.00"),
                RiskLevel.LOW,
                RiskLevel.LOW,
                false,
                null,
                GeographicLevel.STATE_UT,
                "Karnataka",
                "29",
                null,
                legacyJson,
                "[{\"factor\":\"HISTORICAL_INCIDENT\",\"available\":true}]",
                RiskScoringMethod.BASELINE_WEIGHTED,
                null
        );

        riskScoreRepository.saveAndFlush(legacyScore);

        RiskScore retrieved = riskScoreRepository.findByDecisionId(decisionId).orElseThrow();
        assertNotNull(retrieved.getFactorDetails());

        // Deserialize legacy JSON into updated model: new fields must default cleanly to null
        List<RiskFactorDetail> deserialized = objectMapper.readValue(
                retrieved.getFactorDetails(),
                new TypeReference<List<RiskFactorDetail>>() {}
        );

        assertEquals(1, deserialized.size());
        RiskFactorDetail detail = deserialized.get(0);
        assertEquals(RiskFactorType.HISTORICAL_INCIDENT, detail.factor());
        assertThat(detail.weight()).isEqualByComparingTo("0.30");
        assertThat(detail.normalizedValue()).isEqualByComparingTo("50.0");
        assertEquals("MoRTH Road Accidents in India 2024", detail.source());

        // The 6 new provenance fields gracefully deserialize as null from legacy records
        assertNull(detail.sourceType());
        assertNull(detail.sourceIdentifier());
        assertNull(detail.observedAt());
        assertNull(detail.freshnessSeconds());
        assertNull(detail.geographicScope());
        assertNull(detail.normalizationDetails());
    }

    @Test
    @DisplayName("Real MySQL: persists SACHET CRITICAL override audit columns alongside factor details")
    void realMysql_persistsSachetCriticalOverrideAuditDecision() throws Exception {
        UUID decisionId = UUID.randomUUID();
        createdDecisionIds.add(decisionId);

        String factorDetailsJson = "[]";
        RiskScore overrideScore = new RiskScore(
                decisionId,
                testTourist,
                30,
                new BigDecimal("30.00"),
                RiskLevel.LOW,
                RiskLevel.CRITICAL,
                true,
                "NDMA-2026-CYC-0042",
                GeographicLevel.STATE_UT,
                "Karnataka",
                "29",
                "{}",
                factorDetailsJson,
                "[]",
                RiskScoringMethod.BASELINE_WEIGHTED,
                null
        );

        riskScoreRepository.saveAndFlush(overrideScore);

        RiskScore retrieved = riskScoreRepository.findByDecisionId(decisionId).orElseThrow();
        assertTrue(retrieved.isOverrideActive());
        assertEquals(RiskLevel.CRITICAL, retrieved.getEffectiveRiskLevel());
        assertEquals(RiskLevel.LOW, retrieved.getRiskLevel(), "Baseline level must be preserved");
        assertEquals("NDMA-2026-CYC-0042", retrieved.getSachetAlertIdentifier());
        assertEquals("Karnataka", retrieved.getGeographicUnit());
        assertEquals("29", retrieved.getStateCode());
    }
}
