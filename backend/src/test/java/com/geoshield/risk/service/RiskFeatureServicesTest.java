package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.incident.entity.IncidentSourceType;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.geo.StateBoundaryIndex;
import com.geoshield.risk.timeofday.MorthTimeOfDayDistribution;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiskFeatureServicesTest {
    @Mock private HistoricalDataService historicalDataService;

    @Test
    void stateHistoricalFeatureUsesActualMorthPerLakhMetricAndRelativeMaximumNormalization() {
        String metric = "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024";
        when(historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT)).thenReturn(List.of(
                record("Goa", metric, 50), record("Kerala", metric, 100), record("Goa", metric, 99, true)));
        var feature = new HistoricalRiskFeatureService(historicalDataService)
                .historicalIncidentRisk(GeographicResolution.resolved(GeographicLevel.STATE_UT, "Goa"));
        assertTrue(feature.available());
        assertEquals(0, feature.value().compareTo(new BigDecimal("50.00000000")));
        assertTrue(feature.reason().contains("not tourist-specific"));
    }

    @Test
    void unresolvedGeographyAndIncidentRecordsDoNotProduceFabricatedScores() {
        // (1, 1) is in the Gulf of Guinea, so the real resolver finds no containing State/UT.
        var geographic = new BoundaryGeographicResolutionService(new StateBoundaryIndex())
                .resolve(BigDecimal.ONE, BigDecimal.ONE);
        assertFalse(geographic.resolved());
        assertNull(geographic.geographicUnit());
        var incident = new IncidentRiskFeatureService().userReportRisk(List.of(new IncidentResponse(UUID.randomUUID(), "Hazard",
                "Description", BigDecimal.ONE, BigDecimal.ONE, "REPORTED", "a".repeat(64), IncidentSourceType.USER_REPORTED, Instant.now())));
        assertFalse(incident.available());
        assertTrue(incident.reason().contains("normalization"));
    }

    @Test
    void categorizesCurrentTimeIntoArchitectureAlignedThreeHourBandUsingOnlyPublishedMorthValues() {
        // 22:15Z is 03:45 Indian local time, so MoRTH's 03:00 to 06:00 band applies.
        TimeOfDayRiskService service = new TimeOfDayRiskService(
                Clock.fixed(Instant.parse("2026-01-01T22:15:00Z"), ZoneOffset.UTC), new MorthTimeOfDayDistribution());
        assertEquals(3, service.currentBand().startHour());
        assertEquals(6, service.currentBand().endHour());
        var feature = service.currentRisk();
        assertTrue(feature.available());
        // The published 23,398 accidents relative to the 1,02,897 peak band; nothing is synthesized.
        assertEquals(new BigDecimal("22.73924410"), feature.value());
    }

    private HistoricalSafetyRecordSummary record(String unit, String metric, int value) { return record(unit, metric, value, false); }
    private HistoricalSafetyRecordSummary record(String unit, String metric, int value, boolean touristSpecific) {
        return new HistoricalSafetyRecordSummary("MoRTH Road Accidents in India 2024", 2024, GeographicLevel.STATE_UT,
                unit, "Road injuries", metric, BigDecimal.valueOf(value), touristSpecific);
    }
}
