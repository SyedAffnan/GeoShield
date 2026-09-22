package com.geoshield.risk.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.geoshield.common.exception.GlobalExceptionHandler;
import com.geoshield.config.SecurityProperties;
import com.geoshield.risk.dto.HistoricalTrendAdvisoryResponse;
import com.geoshield.risk.dto.ModelProvenanceDto;
import com.geoshield.risk.service.HistoricalTrendAdvisoryService;
import com.geoshield.security.JwtTokenService;
import com.geoshield.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {HistoricalTrendAdvisoryController.class})
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class, HistoricalTrendAdvisoryControllerTest.TestSecurityConfig.class})
class HistoricalTrendAdvisoryControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        public SecurityProperties securityProperties() {
            return new SecurityProperties(12);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HistoricalTrendAdvisoryService advisoryService;

    @MockBean
    private JwtTokenService jwtTokenService;

    private HistoricalTrendAdvisoryResponse createSampleAvailableResponse() {
        ModelProvenanceDto provenance = new ModelProvenanceDto(
                "RF-STATE-EXP-A",
                "2024_TEMPORAL_HOLDOUT",
                "1.0.0-p0",
                null,
                "UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT",
                "geosheild_ml_lagged_features.csv",
                "e2eba6a1f3276d9b345827d22ac3dfd1a524107d6539eb23a96aac211aff5167",
                "2026-09-22T20:30:00Z",
                null,
                "UNAVAILABLE_NOT_RECORDED_IN_ORIGINAL_EXPERIMENT",
                "2021-2023",
                107,
                2024,
                35,
                Map.of("r2", 0.9122, "mae", 3.879, "rmse", 6.1331, "baseline_mae", 16.4128),
                Map.of(
                        "n_estimators", 300,
                        "max_depth", 5,
                        "min_samples_split", 5,
                        "min_samples_leaf", 2,
                        "random_state", 42,
                        "n_jobs", 1
                ),
                "Advisory indicator only."
        );

        return HistoricalTrendAdvisoryResponse.available(
                "Karnataka",
                new BigDecimal("27.8913"),
                "Historical 2024 temporal-holdout model estimate for Karnataka: 27.89 fatalities per 100 accidents.",
                provenance
        );
    }

    @Test
    @DisplayName("TOURIST role can query historical trend advisory -> 200 OK and wire contract verified")
    void touristCanQueryHistoricalAdvisory() throws Exception {
        UUID touristId = UUID.randomUUID();
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        touristId, null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TOURIST"))
                );

        when(advisoryService.getAdvisoryForUser(eq(touristId))).thenReturn(createSampleAvailableResponse());

        mockMvc.perform(get("/api/v1/risk/historical-advisory")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data.advisoryType").value("HISTORICAL_ACCIDENT_TREND_ADVISORY"))
                .andExpect(jsonPath("$.data.geographicLevel").value("STATE_UT"))
                .andExpect(jsonPath("$.data.geographicUnit").value("Karnataka"))
                .andExpect(jsonPath("$.data.parentUnit").value("India"))
                .andExpect(jsonPath("$.data.targetYear").value(2024))
                .andExpect(jsonPath("$.data.predictedAccidentSeverity").value(27.8913))
                .andExpect(jsonPath("$.data.severityMetricUnit").value("fatalities_per_100_accidents"))
                .andExpect(jsonPath("$.data.advisoryNotice").value(org.hamcrest.Matchers.containsString("Karnataka")))
                .andExpect(jsonPath("$.data.scopeDisclaimer").value(HistoricalTrendAdvisoryResponse.MANDATORY_DISCLAIMER))
                .andExpect(jsonPath("$.data.provenance.experimentIdentifier").value("RF-STATE-EXP-A"))
                .andExpect(jsonPath("$.data.provenance.predictionSource").value("2024_TEMPORAL_HOLDOUT"))
                .andExpect(jsonPath("$.data.provenance.modelArtifactStatus").value("UNAVAILABLE_FOR_THIS_TEMPORAL_HOLDOUT"))
                .andExpect(jsonPath("$.data.provenance.modelConfiguration.n_estimators").value(300))
                .andExpect(jsonPath("$.data.provenance.modelConfiguration.max_depth").value(5));

        verify(advisoryService).getAdvisoryForUser(touristId);
    }

    @Test
    @DisplayName("RESPONDER role cannot query historical advisory -> 403 Forbidden")
    @WithMockUser(roles = "RESPONDER")
    void responderCannotQueryHistoricalAdvisory() throws Exception {
        mockMvc.perform(get("/api/v1/risk/historical-advisory"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN role cannot query historical advisory -> 403 Forbidden")
    @WithMockUser(roles = "ADMIN")
    void adminCannotQueryHistoricalAdvisory() throws Exception {
        mockMvc.perform(get("/api/v1/risk/historical-advisory"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous unauthenticated request -> 403 Forbidden (Spring Security default entry point)")
    void anonymousCannotQueryHistoricalAdvisory() throws Exception {
        mockMvc.perform(get("/api/v1/risk/historical-advisory"))
                .andExpect(status().isForbidden());
    }
}
