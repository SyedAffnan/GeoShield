package com.geoshield.historicaldata.ingestion;

import com.geoshield.common.exception.ValidationException;
import com.geoshield.historicaldata.entity.GeographicLevel;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MorthHistoricalDataImporter implements HistoricalDataImporter {
    private static final String GEOGRAPHIC_UNIT = "State/UT";
    private static final String CATEGORY = "State / UT - wise Total Number of Persons Injured in Road Accidents";
    private static final List<String> METRIC_COLUMNS = List.of(
            "State / UT - wise Total Number of Persons Injured in Road Accidents during - 2024 - Number",
            "State / UT - wise Total Number of Persons Injured in Road Accidents during - 2024 - Rank",
            "Share of States / UTs in Total Number of Persons Injured in Road Accidents - 2024",
            "Total Number of Persons Injured in Road Accidents Per Lakh Population - 2024",
            "Total Number of Persons injured in Road Accidents per 10,000 Vehicles - 2022",
            "Total Number of Persons injured in Road Accidents per 10,000 Km of Roads - 2022 (P)");

    @Override
    public HistoricalDataset dataset() {
        return HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024;
    }

    @Override
    public List<HistoricalSafetyRecordDraft> read(Path sourceFile) {
        List<HistoricalSafetyRecordDraft> records = new ArrayList<>();
        for (Map<String, String> row : CsvDatasetReader.read(sourceFile)) {
            String geographicUnit = required(row, GEOGRAPHIC_UNIT);
            GeographicLevel geographicLevel = "Total".equalsIgnoreCase(geographicUnit)
                    ? GeographicLevel.NATIONAL : GeographicLevel.STATE_UT;
            String normalizedUnit = geographicLevel == GeographicLevel.NATIONAL ? "India" : geographicUnit;
            int rowMetrics = 0;
            for (String metricColumn : METRIC_COLUMNS) {
                String value = required(row, metricColumn);
                if ("NA".equalsIgnoreCase(value)) {
                    continue;
                }
                BigDecimal metricValue = numeric(value, metricColumn);
                records.add(new HistoricalSafetyRecordDraft(dataset().source(), dataset().sourceYear(), geographicLevel,
                        normalizedUnit, CATEGORY, metricColumn, metricValue, false));
                rowMetrics++;
            }
            if (rowMetrics == 0) {
                throw new ValidationException("MoRTH row has no supported numeric metric: " + geographicUnit);
            }
        }
        return records;
    }

    private String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new ValidationException("MoRTH dataset is missing required column or value: " + column);
        }
        return value;
    }

    private BigDecimal numeric(String value, String column) {
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.signum() < 0) {
                throw new ValidationException("MoRTH metric must not be negative: " + column);
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new ValidationException("MoRTH metric is not numeric: " + column);
        }
    }
}
