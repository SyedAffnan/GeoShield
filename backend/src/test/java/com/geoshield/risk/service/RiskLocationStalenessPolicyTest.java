package com.geoshield.risk.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ResourceNotFoundException;
import com.geoshield.incident.service.IncidentService;
import com.geoshield.location.service.LocationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Requirement F: When no valid current location exists (due to staleness, inaccuracy,
 * or absence), the risk evaluation path must NOT proceed and must NOT substitute
 * stale coordinates, centroids, zeroes, or synthetic locations downstream.
 */
@ExtendWith(MockitoExtension.class)
class RiskLocationStalenessPolicyTest {

    @Mock private LocationService locationService;
    @Mock private IncidentService incidentService;
    @Mock private GeographicResolutionService geographicResolutionService;
    @Mock private HistoricalRiskFeatureService historicalRiskFeatureService;
    @Mock private IncidentRiskFeatureService incidentRiskFeatureService;
    @Mock private TimeOfDayRiskService timeOfDayRiskService;
    @Mock private WeatherRiskService weatherRiskService;
    @Mock private EmergencyServiceProximityRiskService emergencyServiceProximityRiskService;
    @Mock private RiskFusionService riskFusionService;

    private RiskContextAssembler assembler;
    private RiskApiServiceImpl riskApiService;

    @BeforeEach
    void setUp() {
        assembler = new RiskContextAssembler(locationService, incidentService,
                geographicResolutionService, historicalRiskFeatureService,
                incidentRiskFeatureService, timeOfDayRiskService,
                weatherRiskService, emergencyServiceProximityRiskService);
        riskApiService = new RiskApiServiceImpl(assembler, riskFusionService);
    }

    @Test
    @DisplayName("Assembler throws ResourceNotFoundException when location is stale/inaccurate and never calls downstream services")
    void assembleForCurrentUser_abortsWithoutUsingStaleOrSyntheticCoordinates() {
        UUID userId = UUID.randomUUID();
        when(locationService.getCurrentLocation(userId))
                .thenThrow(new ResourceNotFoundException("Current valid location not found: Location is stale"));

        assertThatThrownBy(() -> assembler.assembleForCurrentUser(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("stale");

        // Verify that downstream services are NEVER called with any coordinates or fallback values
        verify(geographicResolutionService, never()).resolve(any(BigDecimal.class), any(BigDecimal.class));
        verify(incidentRiskFeatureService, never()).userReportRisk(any(), any(BigDecimal.class), any(BigDecimal.class));
        verify(weatherRiskService, never()).currentRisk(any(BigDecimal.class), any(BigDecimal.class));
        verify(emergencyServiceProximityRiskService, never()).proximityRisk(any(BigDecimal.class), any(BigDecimal.class));
        verify(incidentService, never()).getActiveIncidents();
    }

    @Test
    @DisplayName("RiskApiService propagates ResourceNotFoundException and never calculates or persists a score")
    void getCurrentRisk_propagatesExceptionAndNeverFusesOrPersists() {
        UUID userId = UUID.randomUUID();
        when(locationService.getCurrentLocation(userId))
                .thenThrow(new ResourceNotFoundException("Current valid location not found: Location accuracy 150.0m exceeds threshold"));

        assertThatThrownBy(() -> riskApiService.getCurrentRisk(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Current valid location not found")
                .hasMessageContaining("accuracy 150.0m");

        // Verify fusion engine is never invoked
        verify(riskFusionService, never()).calculateBaselineRisk(any());
    }
}
