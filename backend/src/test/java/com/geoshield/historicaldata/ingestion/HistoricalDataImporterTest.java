package com.geoshield.historicaldata.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.geoshield.historicaldata.entity.GeographicLevel;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalDataImporterTest {
    private final MorthHistoricalDataImporter morthImporter = new MorthHistoricalDataImporter();
    private final NcrbHistoricalDataImporter ncrbImporter = new NcrbHistoricalDataImporter();

    @Test
    void normalizesMorthStateAndNationalRowsWithoutTouristSpecificData() throws Exception {
        List<HistoricalSafetyRecordDraft> records = morthImporter.read(resource("historicaldata/morth-annexure-4-fixture.csv"));

        assertEquals(11, records.size());
        assertTrue(records.stream().allMatch(record -> record.sourceYear() == 2024));
        assertTrue(records.stream().allMatch(record -> !record.touristSpecific()));
        assertTrue(records.stream().anyMatch(record -> record.geographicLevel() == GeographicLevel.STATE_UT
                && record.geographicUnit().equals("Andhra Pradesh")));
        assertTrue(records.stream().anyMatch(record -> record.geographicLevel() == GeographicLevel.NATIONAL
                && record.geographicUnit().equals("India")));
    }

    @Test
    void normalizesNcrbForeignTouristsSeparatelyFromOtherAndTotalForeigners() throws Exception {
        List<HistoricalSafetyRecordDraft> records = ncrbImporter.read(resource("historicaldata/ncrb-2023-table-13a-2-fixture.csv"));

        assertEquals(6, records.size());
        assertEquals(2, records.stream().filter(HistoricalSafetyRecordDraft::touristSpecific).count());
        assertTrue(records.stream().filter(HistoricalSafetyRecordDraft::touristSpecific)
                .allMatch(record -> record.metricName().contains("Foreign Tourists")));
        assertFalse(records.stream().filter(record -> !record.metricName().contains("Foreign Tourists"))
                .anyMatch(HistoricalSafetyRecordDraft::touristSpecific));
        assertTrue(records.stream().allMatch(record -> record.geographicLevel() == GeographicLevel.NATIONAL));
    }

    private Path resource(String name) throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource(name).toURI());
    }
}
