package com.geoshield.risk.dto;

import java.math.BigDecimal;

/**
 * Client-facing contract for the retrospective Historical Trend Advisory.
 *
 * <p>Strictly informational and decoupled from the authoritative GeoShield Safety Score.
 */
public record HistoricalTrendAdvisoryResponse(
        String status,
        String advisoryType,
        String geographicLevel,
        String geographicUnit,
        String parentUnit,
        Integer targetYear,
        BigDecimal predictedAccidentSeverity,
        String severityMetricUnit,
        String advisoryNotice,
        String scopeDisclaimer,
        ModelProvenanceDto provenance
) {
    public static final String MANDATORY_DISCLAIMER =
            "This is a retrospective 2024 model-evaluation result for regional road-accident severity. "
            + "It is not a current condition, forecast, tourist-safety assessment, or GeoShield Safety Score.";

    public static final String ADVISORY_TYPE = "HISTORICAL_ACCIDENT_TREND_ADVISORY";
    public static final String METRIC_UNIT = "fatalities_per_100_accidents";

    public static HistoricalTrendAdvisoryResponse available(
            String geographicUnit,
            BigDecimal predictedValue,
            String notice,
            ModelProvenanceDto provenance) {
        return new HistoricalTrendAdvisoryResponse(
                "AVAILABLE",
                ADVISORY_TYPE,
                "STATE_UT",
                geographicUnit,
                "India",
                2024,
                predictedValue,
                METRIC_UNIT,
                notice,
                MANDATORY_DISCLAIMER,
                provenance
        );
    }

    public static HistoricalTrendAdvisoryResponse unavailable(String notice) {
        return new HistoricalTrendAdvisoryResponse(
                "UNAVAILABLE",
                ADVISORY_TYPE,
                "STATE_UT",
                null,
                "India",
                2024,
                null,
                null,
                notice,
                MANDATORY_DISCLAIMER,
                null
        );
    }
}
