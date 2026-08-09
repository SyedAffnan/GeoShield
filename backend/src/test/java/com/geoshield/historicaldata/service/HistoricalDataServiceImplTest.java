package com.geoshield.historicaldata.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.geoshield.common.exception.ValidationException;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.entity.HistoricalSourceType;
import com.geoshield.historicaldata.ingestion.HistoricalDataImporter;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import com.geoshield.historicaldata.ingestion.HistoricalSafetyRecordDraft;
import com.geoshield.historicaldata.repository.HistoricalSafetyRecordRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HistoricalDataServiceImplTest {
    @Mock
    private HistoricalSafetyRecordRepository repository;

    @Test
    void persistsValidMorthRecordWithFixedHistoricalSourceType() {
        HistoricalSafetyRecordDraft draft = draft(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, false, BigDecimal.valueOf(10));
        HistoricalDataServiceImpl service = service(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, List.of(draft));
        when(repository.findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
                anyString(), anyInt(), any(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        HistoricalDataImportResult result = service.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, Path.of("ignored.csv"));

        ArgumentCaptor<HistoricalSafetyRecord> saved = ArgumentCaptor.forClass(HistoricalSafetyRecord.class);
        verify(repository).save(saved.capture());
        assertEquals(HistoricalSourceType.HISTORICAL, saved.getValue().getSourceType());
        assertFalse(saved.getValue().isTouristSpecific());
        assertEquals(1, result.recordsImported());
    }

    @Test
    void skipsExistingNaturalKeyForRepeatSafeImport() {
        HistoricalSafetyRecordDraft draft = draft(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, false, BigDecimal.ONE);
        HistoricalDataServiceImpl service = service(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, List.of(draft));
        when(repository.findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
                anyString(), anyInt(), any(), anyString(), anyString(), anyString())).thenReturn(Optional.of(existingRecord()));

        HistoricalDataImportResult result = service.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, Path.of("ignored.csv"));

        assertEquals(1, result.recordsSkipped());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsMissingRequiredValuesAndNegativeMetrics() {
        HistoricalDataServiceImpl missingSource = service(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024,
                List.of(new HistoricalSafetyRecordDraft("", 2024, GeographicLevel.STATE_UT, "Goa", "Road accidents",
                        "Persons injured", BigDecimal.ONE, false)));
        HistoricalDataServiceImpl negativeMetric = service(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024,
                List.of(draft(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, false, BigDecimal.valueOf(-1))));

        assertThrows(ValidationException.class,
                () -> missingSource.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, Path.of("ignored.csv")));
        assertThrows(ValidationException.class,
                () -> negativeMetric.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, Path.of("ignored.csv")));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsTouristSpecificMorthAndUnsupportedNcrbTouristMetrics() {
        HistoricalDataServiceImpl touristMorth = service(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024,
                List.of(draft(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, true, BigDecimal.ONE)));
        HistoricalDataServiceImpl unsupportedNcrb = service(HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2,
                List.of(draft(HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2, true, BigDecimal.ONE)));

        assertThrows(ValidationException.class,
                () -> touristMorth.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, Path.of("ignored.csv")));
        assertThrows(ValidationException.class,
                () -> unsupportedNcrb.importDataset(HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2, Path.of("ignored.csv")));
    }

    @Test
    void entityDoesNotIntroduceUnsupportedGpsPrecision() {
        List<String> fieldNames = Arrays.stream(HistoricalSafetyRecord.class.getDeclaredFields())
                .map(field -> field.getName()).toList();

        assertFalse(fieldNames.contains("latitude"));
        assertFalse(fieldNames.contains("longitude"));
        assertFalse(fieldNames.contains("incidentDate"));
    }

    private HistoricalDataServiceImpl service(HistoricalDataset dataset, List<HistoricalSafetyRecordDraft> drafts) {
        HistoricalDataImporter importer = new HistoricalDataImporter() {
            @Override public HistoricalDataset dataset() { return dataset; }
            @Override public List<HistoricalSafetyRecordDraft> read(Path sourceFile) { return drafts; }
        };
        return new HistoricalDataServiceImpl(repository, new HistoricalSafetyRecordValidator(), List.of(importer));
    }

    private HistoricalSafetyRecordDraft draft(HistoricalDataset dataset, boolean touristSpecific, BigDecimal metricValue) {
        return new HistoricalSafetyRecordDraft(dataset.source(), dataset.sourceYear(), GeographicLevel.STATE_UT, "Goa",
                "Road accidents", "Persons injured", metricValue, touristSpecific);
    }

    private HistoricalSafetyRecord existingRecord() {
        return new HistoricalSafetyRecord(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024.source(), 2024,
                GeographicLevel.STATE_UT, "Goa", "Road accidents", "Persons injured", BigDecimal.ONE, false);
    }
}
