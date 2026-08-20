package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.service.LocationService;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
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
    @Mock private IncidentService incidentService;
    @Mock private GeographicResolutionService geographicResolutionService;
    @Mock private HistoricalRiskFeatureService historicalRiskFeatureService;
    @Mock private IncidentRiskFeatureService incidentRiskFeatureService;
    @Mock private TimeOfDayRiskService timeOfDayRiskService;

    @Test
    void usesModuleBoundariesAndPassesOnlyExplicitFeatureAvailabilityToBaseline() {
        UUID userId = UUID.randomUUID();
        when(locationService.getCurrentLocation(userId)).thenReturn(new LocationResponse(1L, BigDecimal.ONE, BigDecimal.ONE,
                null, null, Instant.now()));
        List<IncidentResponse> incidents = List.of(new IncidentResponse(UUID.randomUUID(), "Road hazard", "Debris",
                BigDecimal.ONE, BigDecimal.ONE, "REPORTED", "a".repeat(64), IncidentSourceType.USER_REPORTED, Instant.now()));
        when(incidentService.getIncidents(userId)).thenReturn(incidents);
        GeographicResolution resolution = GeographicResolution.unresolved("No mapping");
        when(geographicResolutionService.resolve(BigDecimal.ONE, BigDecimal.ONE)).thenReturn(resolution);
        when(historicalRiskFeatureService.historicalIncidentRisk(resolution)).thenReturn(feature(RiskFactorType.HISTORICAL_INCIDENT));
        when(incidentRiskFeatureService.userReportRisk(incidents)).thenReturn(feature(RiskFactorType.USER_REPORT));
        when(timeOfDayRiskService.currentRisk()).thenReturn(feature(RiskFactorType.TIME_OF_DAY));

        var context = new RiskContextAssembler(locationService, incidentService, geographicResolutionService,
                historicalRiskFeatureService, incidentRiskFeatureService, timeOfDayRiskService).assembleForCurrentUser(userId);

        verify(locationService).getCurrentLocation(userId);
        verify(incidentService).getIncidents(userId);
        verify(geographicResolutionService).resolve(BigDecimal.ONE, BigDecimal.ONE);
        assertFalse(context.historicalIncidentRisk().available());
        assertTrue(context.historicalIncidentRisk().unavailabilityReason().contains("No mapping"));
        assertFalse(context.userReportRisk().available());
    }

    private NormalizedRiskFeature feature(RiskFactorType type) {
        return NormalizedRiskFeature.unavailable(type, "test", "No mapping", "No fabricated score");
    }
}
