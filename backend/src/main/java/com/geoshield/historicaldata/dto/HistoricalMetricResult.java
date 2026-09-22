package com.geoshield.historicaldata.dto;

import com.geoshield.historicaldata.entity.GeographicLevel;

public record HistoricalMetricResult(
        HistoricalSafetyRecordSummary record,
        GeographicLevel actualLevelUsed,
        boolean fallbackApplied,
        String fallbackReason) {

    public static HistoricalMetricResult direct(HistoricalSafetyRecordSummary record) {
        return new HistoricalMetricResult(record, record != null ? record.geographicLevel() : null, false, null);
    }

    public static HistoricalMetricResult fallback(HistoricalSafetyRecordSummary stateRecord, String reason) {
        return new HistoricalMetricResult(stateRecord, GeographicLevel.STATE_UT, true, reason);
    }

    public static HistoricalMetricResult unavailable(String reason) {
        return new HistoricalMetricResult(null, null, false, reason);
    }
}
