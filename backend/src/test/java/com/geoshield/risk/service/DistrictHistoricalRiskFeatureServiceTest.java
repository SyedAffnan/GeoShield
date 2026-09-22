package com.geoshield.risk.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.geoshield.historicaldata.dto.HistoricalMetricResult;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistrictHistoricalRiskFeatureServiceTest {
    private static final String MORTH_SOURCE = "MoRTH Road Accidents in India 2024";
    private static final String CATEGORY = "State / UT - wise Total Number of Persons Injured in Road Accidents";
    private static final String METRIC_NAME = "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024";

    @Mock
    private HistoricalDataService historicalDataService;

    private HistoricalRiskFeatureService defaultService;
    private HistoricalRiskFeatureService enabledService;

    @BeforeEach
    void setUp() {
        // Default service has district evaluation disabled (production default)
        defaultService = new HistoricalRiskFeatureService(historicalDataService, false);
        // Enabled service for controlled district test scenarios
        enabledService = new HistoricalRiskFeatureService(historicalDataService, true);
    }

    private void stateBaselinesAreAvailable() {
        when(historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT)).thenReturn(List.of(
                new HistoricalSafetyRecordSummary(MORTH_SOURCE, 2024, GeographicLevel.STATE_UT, "India", "Kerala",
                        CATEGORY, METRIC_NAME, new BigDecimal("152.6"), false),
                new HistoricalSafetyRecordSummary(MORTH_SOURCE, 2024, GeographicLevel.STATE_UT, "India", "Karnataka",
                        CATEGORY, METRIC_NAME, new BigDecimal("77.2"), false)));
    }

    @Test
    @DisplayName("Default configuration keeps direct district risk evaluation disabled and falls back to State/UT")
    void defaultConfigurationKeepsDirectDistrictRiskDisabled() {
        stateBaselinesAreAvailable();
        HistoricalSafetyRecordSummary stateSummary = new HistoricalSafetyRecordSummary(
                MORTH_SOURCE, 2024, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2"), false);

        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.fallback(stateSummary, "DISTRICT_EVALUATION_DISABLED"));

        GeographicResolution resolution = GeographicResolution.resolvedDistrict(
                "Bengaluru Urban", "Karnataka", "29", null);

        NormalizedRiskFeature feature = defaultService.historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals("HISTORICAL", feature.sourceType());
        assertEquals("MoRTH-2024-STATE-UT", feature.sourceIdentifier());
        assertEquals("STATE_UT: Karnataka [DISTRICT_FALLBACK: Bengaluru Urban]", feature.geographicScope());
        assertTrue(feature.normalizationDetails().contains("[FALLBACK: DISTRICT_EVALUATION_DISABLED]"));
        assertEquals(new BigDecimal("50.58977720"), feature.value());
    }

    @Test
    @DisplayName("When gated feature is enabled, direct district resolution emits DISTRICT provenance")
    void directDistrictResolutionEmitsDistrictProvenanceWhenEnabled() {
        stateBaselinesAreAvailable();
        HistoricalSafetyRecordSummary districtSummary = new HistoricalSafetyRecordSummary(
                MORTH_SOURCE, 2024, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, METRIC_NAME, new BigDecimal("68.4"), false);

        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.direct(districtSummary));

        GeographicResolution resolution = GeographicResolution.resolvedDistrict(
                "Bengaluru Urban", "Karnataka", "29", null);

        NormalizedRiskFeature feature = enabledService.historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.HISTORICAL_INCIDENT, feature.factor());
        assertEquals(MORTH_SOURCE, feature.source());
        assertEquals("HISTORICAL", feature.sourceType());
        assertEquals("MoRTH-2024-DISTRICT", feature.sourceIdentifier());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
        assertEquals("DISTRICT: Karnataka:Bengaluru Urban", feature.geographicScope());
        assertTrue(feature.normalizationDetails().contains("districtMetric"));
    }

    @Test
    @DisplayName("District fallback emits STATE_UT provenance with fallback indicator and preserves State score")
    void districtFallbackEmitsFallbackProvenanceAndPreservesStateScore() {
        stateBaselinesAreAvailable();
        HistoricalSafetyRecordSummary stateSummary = new HistoricalSafetyRecordSummary(
                MORTH_SOURCE, 2024, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, METRIC_NAME, new BigDecimal("77.2"), false);

        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Mysuru", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.fallback(stateSummary, "NO_DISTRICT_RECORD"));

        GeographicResolution resolution = GeographicResolution.resolvedDistrict(
                "Mysuru", "Karnataka", "29", null);

        NormalizedRiskFeature feature = enabledService.historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals(RiskFactorType.HISTORICAL_INCIDENT, feature.factor());
        assertEquals(MORTH_SOURCE, feature.source());
        assertEquals("HISTORICAL", feature.sourceType());
        assertEquals("MoRTH-2024-STATE-UT", feature.sourceIdentifier());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
        assertEquals("STATE_UT: Karnataka [DISTRICT_FALLBACK: Mysuru]", feature.geographicScope());
        assertTrue(feature.normalizationDetails().contains("[FALLBACK: NO_DISTRICT_RECORD]"));
        // Karnataka 77.2 / Kerala 152.6 * 100 = 50.58977720
        assertEquals(new BigDecimal("50.58977720"), feature.value());
    }

    @Test
    @DisplayName("Direct State/UT resolution retains exact Step 4 production behavior")
    void directStateUtResolutionRetainsExactProductionBehavior() {
        stateBaselinesAreAvailable();
        GeographicResolution resolution = GeographicResolution.resolved(
                GeographicLevel.STATE_UT, "Karnataka", "29");

        NormalizedRiskFeature feature = defaultService.historicalIncidentRisk(resolution);

        assertTrue(feature.available());
        assertEquals("HISTORICAL", feature.sourceType());
        assertEquals("MoRTH-2024-STATE-UT", feature.sourceIdentifier());
        assertNull(feature.observedAt());
        assertNull(feature.freshnessSeconds());
        assertEquals("STATE_UT: Karnataka", feature.geographicScope());
        assertEquals(new BigDecimal("50.58977720"), feature.value());
    }

    @Test
    @DisplayName("Specific district unavailability reasons are preserved and surfaced through service (Finding 7)")
    void specificDistrictUnavailabilityReasonsArePreserved() {
        // Test NO_PARENT_STATE
        GeographicResolution noParentResolution = GeographicResolution.resolvedDistrict(
                "Bengaluru Urban", "", "29", null);
        NormalizedRiskFeature noParentFeature = defaultService.historicalIncidentRisk(noParentResolution);
        assertFalse(noParentFeature.available());
        assertEquals("NO_PARENT_STATE", noParentFeature.reasonCode());

        // Test MISSING_DISTRICT_MAPPING
        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "UnknownDist", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.unavailable("MISSING_DISTRICT_MAPPING"));
        GeographicResolution missingDistResolution = GeographicResolution.resolvedDistrict(
                "UnknownDist", "Karnataka", "29", null);
        NormalizedRiskFeature missingDistFeature = enabledService.historicalIncidentRisk(missingDistResolution);
        assertFalse(missingDistFeature.available());
        assertEquals("MISSING_DISTRICT_MAPPING", missingDistFeature.reasonCode());

        // Test INVALID_DISTRICT_METRIC
        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "InvalidDist", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.unavailable("INVALID_DISTRICT_METRIC"));
        GeographicResolution invalidDistResolution = GeographicResolution.resolvedDistrict(
                "InvalidDist", "Karnataka", "29", null);
        NormalizedRiskFeature invalidDistFeature = enabledService.historicalIncidentRisk(invalidDistResolution);
        assertFalse(invalidDistFeature.available());
        assertEquals("INVALID_DISTRICT_METRIC", invalidDistFeature.reasonCode());

        // Test UNSUPPORTED_DISTRICT_YEAR
        when(historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "OldYearDist", MORTH_SOURCE, 2024, "Per Lakh Population"))
                .thenReturn(HistoricalMetricResult.unavailable("UNSUPPORTED_DISTRICT_YEAR"));
        GeographicResolution oldYearResolution = GeographicResolution.resolvedDistrict(
                "OldYearDist", "Karnataka", "29", null);
        NormalizedRiskFeature oldYearFeature = enabledService.historicalIncidentRisk(oldYearResolution);
        assertFalse(oldYearFeature.available());
        assertEquals("UNSUPPORTED_DISTRICT_YEAR", oldYearFeature.reasonCode());
    }
}
