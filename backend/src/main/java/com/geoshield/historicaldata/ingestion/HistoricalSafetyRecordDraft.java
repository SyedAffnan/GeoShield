package com.geoshield.historicaldata.ingestion;

import com.geoshield.historicaldata.entity.GeographicLevel;
import java.math.BigDecimal;

public record HistoricalSafetyRecordDraft(
        String source,
        int sourceYear,
        GeographicLevel geographicLevel,
        String parentUnit,
        String geographicUnit,
        String category,
        String metricName,
        BigDecimal metricValue,
        boolean touristSpecific) {

    public HistoricalSafetyRecordDraft(String source, int sourceYear, GeographicLevel geographicLevel,
            String geographicUnit, String category, String metricName, BigDecimal metricValue, boolean touristSpecific) {
        this(source, sourceYear, geographicLevel, "India", geographicUnit, category, metricName, metricValue, touristSpecific);
    }
}
