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

    public static final String MORTH_DISTRICT_IDENTIFIER = "MoRTH-2024-DISTRICT";

    private final HistoricalDataService historicalDataService;
    private final boolean districtEvaluationEnabled;

    public HistoricalRiskFeatureService(HistoricalDataService historicalDataService) {
        this(historicalDataService, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public HistoricalRiskFeatureService(
            HistoricalDataService historicalDataService,
            @org.springframework.beans.factory.annotation.Value("${geoshield.risk.district-evaluation-enabled:false}") boolean districtEvaluationEnabled) {
        this.historicalDataService = historicalDataService;
        this.districtEvaluationEnabled = districtEvaluationEnabled;
    }

    public NormalizedRiskFeature historicalIncidentRisk(GeographicResolution resolution) {
        if (!resolution.resolved() || (resolution.geographicLevel() != GeographicLevel.STATE_UT
                && resolution.geographicLevel() != GeographicLevel.DISTRICT)) {
            String scope = resolution.resolved() && resolution.geographicLevel() != null
                    ? resolution.geographicLevel().name() + ": " + resolution.geographicUnit()
                    : "COORDINATES_UNRESOLVED";
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    resolution.reason() == null ? "A resolved State/UT or District is required for this feature." : resolution.reason(),
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

        if (resolution.geographicLevel() == GeographicLevel.DISTRICT) {
            if (!districtEvaluationEnabled) {
                // Direct district risk evaluation is gated and disabled by default.
                // District normalization methodology remains OWNER DECISION REQUIRED.
                // Deterministically fall back to parent State/UT evaluation.
                return evaluateDistrictDisabledFallback(resolution);
            }
            return evaluateDistrictRisk(resolution);
        }

        // Current production STATE_UT path: exactly preserved
        return evaluateStateUtRisk(resolution);
    }

    private NormalizedRiskFeature evaluateStateUtRisk(GeographicResolution resolution) {
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

    private NormalizedRiskFeature evaluateDistrictDisabledFallback(GeographicResolution resolution) {
        if (resolution.parentUnit() == null || resolution.parentUnit().isBlank()) {
            String scope = "DISTRICT: " + resolution.parentUnit() + ":" + resolution.geographicUnit();
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "A parent State/UT is required for district resolution.",
                    "Relative maximum normalization is unavailable without a matching metric.",
                    "NO_PARENT_STATE",
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    scope,
                    "Relative maximum normalization is unavailable without a matching metric.");
        }

        var metricResult = historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT,
                resolution.parentUnit(),
                "", // empty target unit forces fallback to parent state
                MORTH_SOURCE,
                2024,
                "Per Lakh Population");

        if (metricResult.record() == null) {
            String scope = "STATE_UT: " + resolution.parentUnit() + " [DISTRICT_FALLBACK: " + resolution.geographicUnit() + "]";
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "No supported historical road-safety metric is available for parent state.",
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

        HistoricalSafetyRecordSummary record = metricResult.record();
        List<HistoricalSafetyRecordSummary> stateRecords = historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT);
        List<HistoricalSafetyRecordSummary> stateRates = stateRecords.stream().filter(r -> MORTH_SOURCE.equals(r.source())
                && !r.touristSpecific() && r.metricName().contains("Per Lakh Population")).toList();
        BigDecimal maximum = stateRates.stream().map(HistoricalSafetyRecordSummary::metricValue).max(BigDecimal::compareTo).orElse(null);

        if (maximum == null || maximum.signum() == 0) {
            String scope = "STATE_UT: " + resolution.parentUnit() + " [DISTRICT_FALLBACK: " + resolution.geographicUnit() + "]";
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "Baseline state maximum normalization is unavailable.",
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

        BigDecimal normalized = record.metricValue().multiply(BigDecimal.valueOf(100)).divide(maximum, 8, RoundingMode.HALF_UP);
        String rawValue = record.metricValue().toPlainString() + " injuries per lakh population";
        String scope = "STATE_UT: " + resolution.parentUnit() + " [DISTRICT_FALLBACK: " + resolution.geographicUnit() + "]";
        String normalizationDetails = "metricValue (" + record.metricValue().stripTrailingZeros().toPlainString()
                + ") / max (" + maximum.stripTrailingZeros().toPlainString() + ") × 100 [FALLBACK: DISTRICT_EVALUATION_DISABLED]";

        return new NormalizedRiskFeature(
                RiskFactorType.HISTORICAL_INCIDENT,
                normalized,
                true,
                MORTH_SOURCE,
                "State/UT-level general road-safety metric (fallback from district " + resolution.geographicUnit() + "); not tourist-specific.",
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

    private NormalizedRiskFeature evaluateDistrictRisk(GeographicResolution resolution) {
        var metricResult = historicalDataService.getSafetyMetricWithFallback(
                GeographicLevel.DISTRICT,
                resolution.parentUnit(),
                resolution.geographicUnit(),
                MORTH_SOURCE,
                2024,
                "Per Lakh Population");

        if (metricResult.record() == null) {
            String scope = "DISTRICT: " + resolution.parentUnit() + ":" + resolution.geographicUnit();
            String unavailabilityReason = (metricResult.fallbackReason() != null && !metricResult.fallbackReason().isBlank())
                    ? metricResult.fallbackReason()
                    : "STATE_METRIC_NOT_FOUND";
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "No supported historical road-safety metric is available for district or parent state.",
                    "Relative maximum normalization is unavailable without a matching metric.",
                    unavailabilityReason,
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    scope,
                    "Relative maximum normalization is unavailable without a matching metric.");
        }

        HistoricalSafetyRecordSummary record = metricResult.record();
        List<HistoricalSafetyRecordSummary> stateRecords = historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT);
        List<HistoricalSafetyRecordSummary> stateRates = stateRecords.stream().filter(r -> MORTH_SOURCE.equals(r.source())
                && !r.touristSpecific() && r.metricName().contains("Per Lakh Population")).toList();
        BigDecimal maximum = stateRates.stream().map(HistoricalSafetyRecordSummary::metricValue).max(BigDecimal::compareTo).orElse(null);

        if (maximum == null || maximum.signum() == 0) {
            String scope = metricResult.fallbackApplied()
                    ? "STATE_UT: " + resolution.parentUnit() + " [DISTRICT_FALLBACK: " + resolution.geographicUnit() + "]"
                    : "DISTRICT: " + resolution.parentUnit() + ":" + resolution.geographicUnit();
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    MORTH_SOURCE,
                    "Baseline state maximum normalization is unavailable.",
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

        BigDecimal normalized = record.metricValue().multiply(BigDecimal.valueOf(100)).divide(maximum, 8, RoundingMode.HALF_UP);
        String rawValue = record.metricValue().toPlainString() + " injuries per lakh population";

        if (metricResult.fallbackApplied()) {
            String scope = "STATE_UT: " + resolution.parentUnit() + " [DISTRICT_FALLBACK: " + resolution.geographicUnit() + "]";
            String normalizationDetails = "metricValue (" + record.metricValue().stripTrailingZeros().toPlainString()
                    + ") / max (" + maximum.stripTrailingZeros().toPlainString() + ") × 100 [FALLBACK: " + metricResult.fallbackReason() + "]";
            return new NormalizedRiskFeature(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    normalized,
                    true,
                    MORTH_SOURCE,
                    "State/UT-level general road-safety metric (fallback from district " + resolution.geographicUnit() + "); not tourist-specific.",
                    "metricValue / maximum same-metric State/UT value × 100",
                    null,
                    rawValue,
                    SOURCE_TYPE,
                    SOURCE_IDENTIFIER,
                    null,
                    null,
                    scope,
                    normalizationDetails);
        } else {
            String scope = "DISTRICT: " + resolution.parentUnit() + ":" + resolution.geographicUnit();
            String normalizationDetails = "districtMetric (" + record.metricValue().stripTrailingZeros().toPlainString()
                    + ") / baselineMax (" + maximum.stripTrailingZeros().toPlainString() + ") × 100";
            return new NormalizedRiskFeature(
                    RiskFactorType.HISTORICAL_INCIDENT,
                    normalized,
                    true,
                    MORTH_SOURCE,
                    "District-level general road-safety metric; not tourist-specific.",
                    "districtMetric / baselineMax × 100",
                    null,
                    rawValue,
                    SOURCE_TYPE,
                    MORTH_DISTRICT_IDENTIFIER,
                    null,
                    null,
                    scope,
                    normalizationDetails);
        }
    }
}
