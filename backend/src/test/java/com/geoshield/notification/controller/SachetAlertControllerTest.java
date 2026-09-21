package com.geoshield.notification.controller;

import com.geoshield.common.api.ApiResponse;
import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.service.SachetAlertService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SachetAlertControllerTest {

    @Mock
    private SachetAlertService alertService;

    private SachetAlertController controller;

    @BeforeEach
    void setUp() {
        controller = new SachetAlertController(alertService);
    }

    @Test
    @DisplayName("Ingest CAP 1.2 XML returns 201 Created with alert summary")
    void ingestAlertSuccess() {
        String capXml = "<alert><identifier>TEST-1</identifier></alert>";
        SachetAlertSummary summary = new SachetAlertSummary(
                UUID.randomUUID(), "TEST-1", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS),
                "Cyclone Headline", "Description", "Shelter", "Area", true
        );

        when(alertService.ingestCapXml(capXml)).thenReturn(summary);

        ResponseEntity<ApiResponse<SachetAlertSummary>> response = controller.ingestAlert(capXml);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().success());
        assertEquals("TEST-1", response.getBody().data().identifier());
        assertEquals("Extreme", response.getBody().data().severity());
        verify(alertService).ingestCapXml(capXml);
    }

    @Test
    @DisplayName("Get active alerts returns list of unexpired disaster alerts")
    void getActiveAlerts() {
        SachetAlertSummary summary = new SachetAlertSummary(
                UUID.randomUUID(), "TEST-1", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS),
                "Cyclone Headline", "Description", "Shelter", "Area", true
        );

        when(alertService.getActiveAlerts()).thenReturn(List.of(summary));

        ApiResponse<List<SachetAlertSummary>> response = controller.getActiveAlerts();

        assertNotNull(response);
        assertTrue(response.success());
        assertEquals(1, response.data().size());
        assertEquals("TEST-1", response.data().get(0).identifier());
        verify(alertService).getActiveAlerts();
    }

    @Test
    @DisplayName("Ingest CAP payload exceeding 512 KB returns 413 Payload Too Large (P11)")
    void ingestOversizedPayloadReturns413() {
        String largeXml = "a".repeat(513 * 1024);

        ResponseEntity<ApiResponse<SachetAlertSummary>> response = controller.ingestAlert(largeXml);

        assertNotNull(response);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        verify(alertService, never()).ingestCapXml(anyString());
    }
}
