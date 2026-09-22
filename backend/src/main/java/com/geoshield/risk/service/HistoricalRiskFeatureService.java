package com.geoshield.risk.service;

import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.service.HistoricalDataService;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HistoricalRiskFeatureService {
    public static final String MORTH_SOURCE = "MoRTH Road Accidents in India 2024";
    public static final String SOURCE_TYPE = "HISTORICAL";
    public static final String SOURCE_IDENTIFIER = "MoRTH-2024-STATE-UT";

    private final HistoricalDataService historicalDataService;

    public HistoricalRiskFeatureService(HistoricalDataService historicalDataService) {
        this.historicalDataService = historicalDataService;
    }

    public NormalizedRiskFeature historicalIncidentRisk(GeographicResolution resolution) {
        if (!resolution.resolved() || resolution.geographicLevel() != GeographicLevel.STATE_UT) {
            String scope = resolution.resolved()
                    ? resolution.geographicLevel().name() + ": " + resolution.geographicUnit()
                    : "COORDINATES_UNRESOLVED";
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    resolution.reason() == null ? "A resolved State/UT is required for this feature." : resolution.reason(),
                    "Requires a verified State/UT geographic resolution.",
                    "HISTORICAL_DATA_UNAVAILABLE",
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    scope,
                    "Requires a verified State/UT geographic resolution.");
        }
        List<HistoricalSafetyRecordSummary> records = historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT);
        List<HistoricalSafetyRecordSummary> rates = records.stream().filter(record -> MORTH_SOURCE.equals(record.source())
                && !record.touristSpecific() && record.metricName().contains("Per Lakh Population")).toList();
        HistoricalSafetyRecordSummary local = rates.stream().filter(record -> resolution.geographicUnit().equalsIgnoreCase(record.geographicUnit()))
                .findFirst().orElse(null);
        BigDecimal maximum = rates.stream().map(HistoricalSafetyRecordSummary::metricValue).max(BigDecimal::compareTo).orElse(null);
        String scope = "STATE_UT: " + resolution.geographicUnit();
        if (local == null || maximum == null || maximum.signum() == 0) {
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "No supported State/UT per-lakh road-injury metric is available for the resolved unit.",
                    "Relative maximum normalization is unavailable without a matching metric.",
                    "STATE_METRIC_NOT_FOUND",
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    scope,
                    "Relative maximum normalization is unavailable without a matching metric.");
        }
        BigDecimal normalized = local.metricValue().multiply(BigDecimal.valueOf(100)).divide(maximum, 8, RoundingMode.HALF_UP);
        String rawValue = local.metricValue().toPlainString() + " injuries per lakh population";
        String normalizationDetails = "metricValue (" + local.metricValue().stripTrailingZeros().toPlainString()
                + ") / max (" + maximum.stripTrailingZeros().toPlainString() + ") × 100";
        return new NormalizedRiskFeature(
                RiskFactorType.HISTORICAL_INCIDENT,
                normalized,
                true,
                MORTH_SOURCE,
                "State/UT-level general road-safety metric; not tourist-specific.",
                "metricValue / maximum same-metric State/UT value × 100",
                null,
                rawValue,
                SOURCE_TYPE,
                SOURCE_IDENTIFIER,
                null,
                null,
                scope,
                normalizationDetails);
    }
}
