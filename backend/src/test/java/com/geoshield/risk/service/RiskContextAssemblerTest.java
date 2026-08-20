package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskContextAssemblerTest {
    @Mock private LocationService locationService;
    @Mock private HistoricalDataService historicalDataService;
    @Mock private IncidentService incidentService;

    @Test
    void usesOnlyModuleServiceBoundariesAndDoesNotFabricateUnavailableScores() {
        UUID userId = UUID.randomUUID();
        when(locationService.getCurrentLocation(userId)).thenReturn(new LocationResponse(1L, BigDecimal.ONE, BigDecimal.ONE,
                null, null, Instant.now()));
        when(historicalDataService.hasHistoricalSafetyRecords()).thenReturn(true);
        when(incidentService.getIncidents(userId)).thenReturn(List.of(new IncidentResponse(UUID.randomUUID(), "Road hazard",
                "Debris", BigDecimal.ONE, BigDecimal.ONE, "REPORTED", "a".repeat(64), IncidentSourceType.USER_REPORTED,
                Instant.now())));

        var context = new RiskContextAssembler(locationService, historicalDataService, incidentService)
                .assembleForCurrentUser(userId);

        verify(locationService).getCurrentLocation(userId);
        verify(historicalDataService).hasHistoricalSafetyRecords();
        verify(incidentService).getIncidents(userId);
        assertFalse(context.historicalIncidentRisk().available());
        assertTrue(context.historicalIncidentRisk().unavailabilityReason().contains("mapping"));
        assertFalse(context.userReportRisk().available());
        assertTrue(context.userReportRisk().unavailabilityReason().contains("normalization"));
    }
}
