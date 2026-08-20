package com.geoshield.risk.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.risk.dto.RiskLevel;
import com.geoshield.risk.dto.RiskResponse;
import com.geoshield.risk.service.RiskApiService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class RiskControllerTest {
    @Mock private RiskApiService riskApiService;
    @Mock private Authentication authentication;

    @Test
    void usesOnlyTheAuthenticatedJwtPrincipalAsTheRiskSubject() {
        UUID authenticatedUserId = UUID.randomUUID();
        RiskResponse risk = new RiskResponse(BigDecimal.ZERO, RiskLevel.LOW, "Normal precautions are recommended.",
                List.of(), "BASELINE_WEIGHTED", null);
        when(authentication.getPrincipal()).thenReturn(authenticatedUserId);
        when(riskApiService.getCurrentRisk(authenticatedUserId)).thenReturn(risk);

        var response = new RiskController(riskApiService).getCurrentRisk(authentication);

        verify(riskApiService).getCurrentRisk(authenticatedUserId);
        assertEquals(risk, response.data());
        assertEquals("BASELINE_WEIGHTED", response.data().modelType());
        assertEquals(null, response.data().modelVersion());
    }
}
