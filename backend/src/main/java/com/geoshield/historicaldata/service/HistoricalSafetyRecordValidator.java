package com.geoshield.historicaldata.service;

import com.geoshield.common.exception.ValidationException;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import com.geoshield.historicaldata.ingestion.HistoricalSafetyRecordDraft;
import java.time.Year;
import org.springframework.stereotype.Component;

@Component
class HistoricalSafetyRecordValidator {
    void validate(HistoricalDataset dataset, HistoricalSafetyRecordDraft record) {
        required(record.source(), "source");
        if (record.sourceYear() < 1900 || record.sourceYear() > Year.now().getValue()) {
            throw new ValidationException("sourceYear must be a valid published year");
        }
        if (record.geographicLevel() == null) {
            throw new ValidationException("geographicLevel is required");
        }
        required(record.geographicUnit(), "geographicUnit");
        required(record.category(), "category");
        required(record.metricName(), "metricName");
        if (record.metricValue() == null || record.metricValue().signum() < 0) {
            throw new ValidationException("metricValue must be non-negative");
        }
        if (dataset == HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024 && record.touristSpecific()) {
            throw new ValidationException("MoRTH records must not be tourist-specific");
        }
        if (dataset == HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2 && record.touristSpecific()
                && !record.metricName().equals("Cases of Crimes Committed against - Foreign Tourists (Col.3)")) {
            throw new ValidationException("NCRB tourist-specific records must originate from the Foreign Tourists column");
        }
    }

    private void required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
    }
}
