package com.geoshield.historicaldata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.geoshield.historicaldata.dto.HistoricalMetricResult;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.repository.HistoricalSafetyRecordRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalDataFallbackServiceTest {
    private static final String MORTH_SOURCE = "MoRTH Road Accidents in India 2024";
    private static final int ACTIVE_YEAR = 2024;
    private static final String PER_LAKH_PATTERN = "Per Lakh Population";
    private static final String CATEGORY = "State / UT - wise Total Number of Persons Injured in Road Accidents";
    private static final String FULL_METRIC = "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024";

    @Mock
    private HistoricalSafetyRecordRepository repository;

    @Mock
    private HistoricalSafetyRecordValidator validator;

    private HistoricalDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HistoricalDataServiceImpl(repository, validator, List.of());
    }

    @Test
    @DisplayName("Direct district lookup returns direct result when authoritative district record exists")
    void directDistrictLookupReturnsDirectResult() {
        HistoricalSafetyRecord districtRecord = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, FULL_METRIC, new BigDecimal("68.4000"), false);

        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT,
                "Karnataka", "Bengaluru Urban", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(districtRecord));

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNotNull(result.record());
        assertEquals(GeographicLevel.DISTRICT, result.actualLevelUsed());
        assertFalse(result.fallbackApplied());
        assertNull(result.fallbackReason());
        assertEquals(new BigDecimal("68.4000"), result.record().metricValue());
        assertEquals("Bengaluru Urban", result.record().geographicUnit());
        assertEquals("Karnataka", result.record().parentUnit());
    }

    @Test
    @DisplayName("Missing district record deterministically triggers fallback to parent State/UT record")
    void noDistrictRecordTriggersFallbackToParentState() {
        HistoricalSafetyRecord stateRecord = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, FULL_METRIC, new BigDecimal("77.2000"), false);

        // District does not exist
        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT,
                "Karnataka", "Mysuru", PER_LAKH_PATTERN))
                .thenReturn(Optional.empty());

        // Parent state exists
        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT,
                "India", "Karnataka", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(stateRecord));

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Mysuru", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNotNull(result.record());
        assertEquals(GeographicLevel.STATE_UT, result.actualLevelUsed());
        assertTrue(result.fallbackApplied());
        assertEquals("NO_DISTRICT_RECORD", result.fallbackReason());
        assertEquals(new BigDecimal("77.2000"), result.record().metricValue());
        assertEquals("Karnataka", result.record().geographicUnit());
    }

    @Test
    @DisplayName("Unsupported district publication year triggers fallback with UNSUPPORTED_DISTRICT_YEAR")
    void unsupportedDistrictYearTriggersFallback() {
        // Obsolete district record from 2018
        HistoricalSafetyRecord staleDistrict = new HistoricalSafetyRecord(
                MORTH_SOURCE, 2018, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, FULL_METRIC, new BigDecimal("55.0000"), false);

        HistoricalSafetyRecord stateRecord = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, FULL_METRIC, new BigDecimal("77.2000"), false);

        // 2024 query returns empty for district
        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT,
                "Karnataka", "Bengaluru Urban", PER_LAKH_PATTERN))
                .thenReturn(Optional.empty());

        // But 2018 district record exists under Karnataka
        when(repository.findAllByGeographicLevelAndParentUnit(GeographicLevel.DISTRICT, "Karnataka"))
                .thenReturn(List.of(staleDistrict));

        // Parent state 2024 exists
        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT,
                "India", "Karnataka", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(stateRecord));

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNotNull(result.record());
        assertEquals(GeographicLevel.STATE_UT, result.actualLevelUsed());
        assertTrue(result.fallbackApplied());
        assertEquals("UNSUPPORTED_DISTRICT_YEAR", result.fallbackReason());
        assertEquals(new BigDecimal("77.2000"), result.record().metricValue());
    }

    @Test
    @DisplayName("Invalid non-positive district metric triggers fallback with INVALID_DISTRICT_METRIC")
    void invalidDistrictMetricTriggersFallback() {
        HistoricalSafetyRecord invalidDistrict = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban",
                CATEGORY, FULL_METRIC, BigDecimal.ZERO, false);

        HistoricalSafetyRecord stateRecord = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, FULL_METRIC, new BigDecimal("77.2000"), false);

        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.DISTRICT,
                "Karnataka", "Bengaluru Urban", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(invalidDistrict));

        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT,
                "India", "Karnataka", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(stateRecord));

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "Bengaluru Urban", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNotNull(result.record());
        assertEquals(GeographicLevel.STATE_UT, result.actualLevelUsed());
        assertTrue(result.fallbackApplied());
        assertEquals("INVALID_DISTRICT_METRIC", result.fallbackReason());
        assertEquals(new BigDecimal("77.2000"), result.record().metricValue());
    }

    @Test
    @DisplayName("Blank district mapping triggers fallback with MISSING_DISTRICT_MAPPING")
    void missingDistrictMappingTriggersFallback() {
        HistoricalSafetyRecord stateRecord = new HistoricalSafetyRecord(
                MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT, "India", "Karnataka",
                CATEGORY, FULL_METRIC, new BigDecimal("77.2000"), false);

        when(repository.findSpecificMetric(MORTH_SOURCE, ACTIVE_YEAR, GeographicLevel.STATE_UT,
                "India", "Karnataka", PER_LAKH_PATTERN))
                .thenReturn(Optional.of(stateRecord));

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Karnataka", "", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNotNull(result.record());
        assertEquals(GeographicLevel.STATE_UT, result.actualLevelUsed());
        assertTrue(result.fallbackApplied());
        assertEquals("MISSING_DISTRICT_MAPPING", result.fallbackReason());
    }

    @Test
    @DisplayName("Null or blank parent state returns unavailable with NO_PARENT_STATE")
    void nullOrBlankParentStateReturnsNoParentState() {
        HistoricalMetricResult nullResult = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, null, "Mysuru", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);
        assertNull(nullResult.record());
        assertFalse(nullResult.fallbackApplied());
        assertEquals("NO_PARENT_STATE", nullResult.fallbackReason());

        HistoricalMetricResult blankResult = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "   ", "Mysuru", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);
        assertNull(blankResult.record());
        assertFalse(blankResult.fallbackApplied());
        assertEquals("NO_PARENT_STATE", blankResult.fallbackReason());
    }

    @Test
    @DisplayName("When fallback to state also fails, the specific district fallback reason is preserved (Finding 7)")
    void missingDistrictAndMissingStatePreservesSpecificReason() {
        when(repository.findSpecificMetric(anyString(), anyInt(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT, "Atlantis", "LostCity", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNull(result.record());
        assertFalse(result.fallbackApplied());
        assertEquals("NO_DISTRICT_RECORD", result.fallbackReason());
    }

    @Test
    @DisplayName("Direct STATE_UT lookup with no match returns STATE_METRIC_NOT_FOUND")
    void directStateUtNotFoundReturnsStateMetricNotFound() {
        when(repository.findSpecificMetric(anyString(), anyInt(), any(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());

        HistoricalMetricResult result = service.getSafetyMetricWithFallback(
                GeographicLevel.STATE_UT, "India", "UnknownState", MORTH_SOURCE, ACTIVE_YEAR, PER_LAKH_PATTERN);

        assertNull(result.record());
        assertFalse(result.fallbackApplied());
        assertEquals("STATE_METRIC_NOT_FOUND", result.fallbackReason());
    }
}
