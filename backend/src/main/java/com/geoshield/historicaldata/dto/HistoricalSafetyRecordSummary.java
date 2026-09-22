package com.geoshield.historicaldata.dto;

import com.geoshield.historicaldata.entity.GeographicLevel;
import java.math.BigDecimal;

/** Read model exposed to other modules without exposing the historical JPA entity. */
public record HistoricalSafetyRecordSummary(
        String source,
        int sourceYear,
        GeographicLevel geographicLevel,
        String parentUnit,
        String geographicUnit,
        String category,
        String metricName,
        BigDecimal metricValue,
        boolean touristSpecific) {

    public HistoricalSafetyRecordSummary(String source, int sourceYear, GeographicLevel geographicLevel,
            String geographicUnit, String category, String metricName, BigDecimal metricValue, boolean touristSpecific) {
        this(source, sourceYear, geographicLevel, "India", geographicUnit, category, metricName, metricValue, touristSpecific);
    }
}
