package com.geoshield.notification.controller;

import com.geoshield.common.exception.GlobalExceptionHandler;
import com.geoshield.config.SecurityProperties;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import com.geoshield.risk.controller.RiskController;
import com.geoshield.risk.dto.BaselineRiskResult;
import com.geoshield.risk.dto.RiskFactorContribution;
import com.geoshield.risk.dto.RiskFactorType;
import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.service.RiskApiService;
import com.geoshield.security.JwtTokenService;
import com.geoshield.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@WebMvcTest(controllers = {SachetAlertController.class, RiskController.class})
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class, SachetAlertSecurityIntegrationTest.TestSecurityConfig.class})
class SachetAlertSecurityIntegrationTest {

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
    private SachetAlertService sachetAlertService;

    @MockBean
    private RiskApiService riskApiService;

    @MockBean
    private JwtTokenService jwtTokenService;

    private static final String SAMPLE_CAP_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <identifier>NDMA-TEST-001</identifier>
              <sender>imd@sachet.gov.in</sender>
              <sent>2026-09-20T10:00:00Z</sent>
              <status>Actual</status>
              <msgType>Alert</msgType>
              <info>
                <category>Met</category>
                <event>Severe Cyclonic Storm</event>
                <urgency>Immediate</urgency>
                <severity>Extreme</severity>
                <certainty>Observed</certainty>
                <expires>2026-09-21T10:00:00Z</expires>
                <headline>Cyclone Warning</headline>
                <area><circle>19.81,85.83 30.0</circle></area>
              </info>
            </alert>
            """;

    @Test
    @DisplayName("ADMIN role can ingest CAP XML -> 201 Created and JSON wire contract verified")
    @WithMockUser(roles = "ADMIN")
    void adminCanIngestCapAlert() throws Exception {
        SachetAlertSummary summary = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-TEST-001", "imd@sachet.gov.in", Instant.now(),
                "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now(), Instant.now().plus(12, ChronoUnit.HOURS),
                "Cyclone Warning", "Storm approaching", "Evacuate", "Puri Coast",
                false, "Actual", "Alert"
        );

        when(sachetAlertService.ingestCapXml(any(String.class))).thenReturn(summary);

        mockMvc.perform(post("/api/v1/alerts/sachet")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(SAMPLE_CAP_XML))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.identifier").value("NDMA-TEST-001"))
                .andExpect(jsonPath("$.data.isSynthetic").value(false))
                .andExpect(jsonPath("$.data.status").value("Actual"))
                .andExpect(jsonPath("$.data.msgType").value("Alert"))
                .andExpect(jsonPath("$.data.severity").value("Extreme"))
                .andExpect(jsonPath("$.data.urgency").value("Immediate"));
    }

    @Test
    @DisplayName("TOURIST role cannot ingest CAP XML -> 403 Forbidden")
    @WithMockUser(roles = "TOURIST")
    void touristCannotIngestCapAlert() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/sachet")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(SAMPLE_CAP_XML))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RESPONDER role cannot ingest CAP XML -> 403 Forbidden")
    @WithMockUser(roles = "RESPONDER")
    void responderCannotIngestCapAlert() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/sachet")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(SAMPLE_CAP_XML))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Anonymous unauthenticated request cannot ingest CAP XML -> 401 or 403 Forbidden")
    void anonymousCannotIngestCapAlert() throws Exception {
        mockMvc.perform(post("/api/v1/alerts/sachet")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(SAMPLE_CAP_XML))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authenticated user can list active alerts -> 200 OK")
    @WithMockUser(roles = "TOURIST")
    void authenticatedUserCanListActiveAlerts() throws Exception {
        SachetAlertSummary summary = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-TEST-001", "imd@sachet.gov.in", Instant.now(),
                "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now(), Instant.now().plus(12, ChronoUnit.HOURS),
                "Cyclone Warning", "Storm approaching", "Evacuate", "Puri Coast",
                true, "Test", "Alert"
        );

        when(sachetAlertService.getActiveAlerts()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/alerts/sachet/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].identifier").value("NDMA-TEST-001"))
                .andExpect(jsonPath("$.data[0].isSynthetic").value(true))
                .andExpect(jsonPath("$.data[0].status").value("Test"));
    }

    @Test
    @DisplayName("Risk API wire contract includes overrideActive, effectiveRiskLevel, activeDisasterAlert")
    void riskApiWireContractSerialization() throws Exception {
        UUID userId = UUID.randomUUID();
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TOURIST"))
                );

        SachetAlertSummary alertSummary = new SachetAlertSummary(
                UUID.randomUUID(), "NDMA-2026-CYC-0042", "imd@sachet.gov.in", Instant.now(),
                "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS),
                "Cyclone Warning", "Very heavy rain", "Evacuate", "Puri Coast",
                false, "Actual", "Alert"
        );

        List<RiskFactorContribution> contributions = List.of(
                new RiskFactorContribution(RiskFactorType.WEATHER, false, null, new BigDecimal("0.20"),
                        BigDecimal.ZERO, "Weather data unavailable.")
        );

        RiskResponse riskResponse = new RiskResponse(
                new BigDecimal("35.00"),
                RiskLevel.LOW,
                "CIVIL DEFENSE DISASTER OVERRIDE: Severe Cyclonic Storm - Evacuate",
                contributions,
                null,
                null,
                "BASELINE_WEIGHTED",
                null,
                true,
                RiskLevel.CRITICAL,
                alertSummary
        );

        when(riskApiService.getCurrentRisk(eq(userId))).thenReturn(riskResponse);

        mockMvc.perform(get("/api/v1/risk")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.safetyScore").value(35.00))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.data.overrideActive").value(true))
                .andExpect(jsonPath("$.data.effectiveRiskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.data.activeDisasterAlert.identifier").value("NDMA-2026-CYC-0042"))
                .andExpect(jsonPath("$.data.activeDisasterAlert.isSynthetic").value(false))
                .andExpect(jsonPath("$.data.activeDisasterAlert.status").value("Actual"))
                .andExpect(jsonPath("$.data.activeDisasterAlert.msgType").value("Alert"));
    }
}
