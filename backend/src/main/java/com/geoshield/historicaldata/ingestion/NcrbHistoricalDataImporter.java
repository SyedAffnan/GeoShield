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
public class NcrbHistoricalDataImporter implements HistoricalDataImporter {
    private static final String CRIME_HEAD = "Crime Head";
    private static final String FOREIGN_TOURISTS = "Cases of Crimes Committed against - Foreign Tourists (Col.3)";
    private static final String OTHER_FOREIGNERS = "Cases of Crimes Committed against - Other Foreigners (Col.4)";
    private static final String TOTAL_FOREIGNERS = "Cases of Crimes Committed against - Total Foreigners (Col.3+Col.4) (Col.5)";

    @Override
    public HistoricalDataset dataset() {
        return HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2;
    }

    @Override
    public List<HistoricalSafetyRecordDraft> read(Path sourceFile) {
        List<HistoricalSafetyRecordDraft> records = new ArrayList<>();
        for (Map<String, String> row : CsvDatasetReader.read(sourceFile)) {
            String crimeHead = required(row, CRIME_HEAD);
            records.add(record(crimeHead, FOREIGN_TOURISTS, required(row, FOREIGN_TOURISTS), true));
            records.add(record(crimeHead, OTHER_FOREIGNERS, required(row, OTHER_FOREIGNERS), false));
            records.add(record(crimeHead, TOTAL_FOREIGNERS, required(row, TOTAL_FOREIGNERS), false));
        }
        return records;
    }

    private HistoricalSafetyRecordDraft record(String crimeHead, String metricName, String value, boolean touristSpecific) {
        return new HistoricalSafetyRecordDraft(dataset().source(), dataset().sourceYear(), GeographicLevel.NATIONAL,
                "India", crimeHead, metricName, numeric(value, metricName), touristSpecific);
    }

    private String required(Map<String, String> row, String column) {
        String value = row.get(column);
        if (value == null || value.isBlank()) {
            throw new ValidationException("NCRB dataset is missing required column or value: " + column);
        }
        return value;
    }

    private BigDecimal numeric(String value, String column) {
        try {
            BigDecimal result = new BigDecimal(value);
            if (result.signum() < 0) {
                throw new ValidationException("NCRB metric must not be negative: " + column);
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new ValidationException("NCRB metric is not numeric: " + column);
        }
    }
}
