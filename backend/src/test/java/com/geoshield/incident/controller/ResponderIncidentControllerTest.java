package com.geoshield.incident.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.incident.dto.IncidentStatusUpdateRequest;
import com.geoshield.incident.dto.ResponderIncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.service.IncidentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ResponderIncidentControllerTest {
    @Mock private IncidentService incidentService;
    @Mock private Authentication authentication;

    private ResponderIncidentController controller;

    @BeforeEach
    void setUp() {
        controller = new ResponderIncidentController(incidentService);
    }

    @Test
    void getActiveIncidentQueueReturnsQueue() {
        ResponderIncidentResponse incident = new ResponderIncidentResponse(
                UUID.randomUUID(), UUID.randomUUID(), "tourist1", "Tourist One", "+919876543210",
                "MEDICAL", "Fell down", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                "REPORTED", IncidentSourceType.USER_REPORTED, Instant.now()
        );
        when(incidentService.getActiveIncidentQueue()).thenReturn(List.of(incident));

        var response = controller.getActiveIncidentQueue();

        assertThat(response.data()).containsExactly(incident);
        verify(incidentService).getActiveIncidentQueue();
    }

    @Test
    void updateStatusCallsIncidentService() {
        UUID incidentId = UUID.randomUUID();
        UUID responderId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(responderId);

        ResponderIncidentResponse updated = new ResponderIncidentResponse(
                incidentId, UUID.randomUUID(), "tourist1", "Tourist One", "+919876543210",
                "MEDICAL", "Fell down", new BigDecimal("12.9716"), new BigDecimal("77.5946"),
                "ACKNOWLEDGED", IncidentSourceType.USER_REPORTED, Instant.now()
        );
        when(incidentService.updateIncidentStatus(incidentId, "ACKNOWLEDGED", responderId)).thenReturn(updated);

        var response = controller.updateStatus(authentication, incidentId, new IncidentStatusUpdateRequest("ACKNOWLEDGED"));

        assertThat(response.data().status()).isEqualTo("ACKNOWLEDGED");
        verify(incidentService).updateIncidentStatus(incidentId, "ACKNOWLEDGED", responderId);
    }
}
