package com.geoshield.risk.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/** Provenance-preserving normalized feature contract for baseline and future AI consumers. */
public record NormalizedRiskFeature(
        RiskFactorType factor,
        BigDecimal value,
        boolean available,
        String source,
        String reason,
        String normalization,
        String reasonCode,
        String rawValue,
        String sourceType,
        String sourceIdentifier,
        Instant observedAt,
        Long freshnessSeconds,
        String geographicScope,
        String normalizationDetails) {

    public NormalizedRiskFeature(
            RiskFactorType factor,
            BigDecimal value,
            boolean available,
            String source,
            String reason,
            String normalization,
            String reasonCode,
            String rawValue) {
        this(factor, value, available, source, reason, normalization, reasonCode, rawValue,
                null, null, null, null, null, normalization);
    }

    public NormalizedRiskFeature(
            RiskFactorType factor,
            BigDecimal value,
            boolean available,
            String source,
            String reason,
            String normalization) {
        this(factor, value, available, source, reason, normalization, null, null,
                null, null, null, null, null, normalization);
    }

    public RiskFactorInput toRiskFactorInput() {
        String effectiveReasonCode = available ? null : (reasonCode != null ? reasonCode : defaultReasonCode(factor, reason));
        String effectiveNorm = normalizationDetails != null ? normalizationDetails : normalization;
        return available
                ? RiskFactorInput.available(value, rawValue, source, sourceType, sourceIdentifier, observedAt, freshnessSeconds, geographicScope, effectiveNorm)
                : RiskFactorInput.unavailable(reason, effectiveReasonCode, source, sourceType, sourceIdentifier, observedAt, freshnessSeconds, geographicScope, effectiveNorm);
    }

    public static NormalizedRiskFeature unavailable(
            RiskFactorType factor,
            String source,
            String reason,
            String normalization) {
        return new NormalizedRiskFeature(factor, null, false, source, reason, normalization, null, null,
                null, null, null, null, null, normalization);
    }

    public static NormalizedRiskFeature unavailable(
            RiskFactorType factor,
            String source,
            String reason,
            String normalization,
            String reasonCode) {
        return new NormalizedRiskFeature(factor, null, false, source, reason, normalization, reasonCode, null,
                null, null, null, null, null, normalization);
    }

    public static NormalizedRiskFeature unavailable(
            RiskFactorType factor,
            String source,
            String reason,
            String normalization,
            String reasonCode,
            String rawValue,
            String sourceType,
            String sourceIdentifier,
            Instant observedAt,
            Long freshnessSeconds,
            String geographicScope,
            String normalizationDetails) {
        return new NormalizedRiskFeature(factor, null, false, source, reason, normalization, reasonCode, rawValue,
                sourceType, sourceIdentifier, observedAt, freshnessSeconds, geographicScope, normalizationDetails);
    }

    public static String defaultReasonCode(RiskFactorType factor, String reason) {
        if (reason == null) {
            return factor.name() + "_UNAVAILABLE";
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return switch (factor) {
            case HISTORICAL_INCIDENT -> {
                if (lower.contains("metric")) yield "STATE_METRIC_NOT_FOUND";
                yield "HISTORICAL_DATA_UNAVAILABLE";
            }
            case WEATHER -> {
                if (lower.contains("wmo") || lower.contains("mapping")) yield "WEATHER_CODE_UNMAPPED";
                if (lower.contains("table 3.8") || lower.contains("severity")) yield "WEATHER_SEVERITY_TABLE_UNAVAILABLE";
                yield "WEATHER_PROVIDER_UNAVAILABLE";
            }
            case TIME_OF_DAY -> {
                if (lower.contains("table 7.3") || lower.contains("interval")) yield "TIME_INTERVAL_UNMAPPED";
                yield "TIME_DISTRIBUTION_UNAVAILABLE";
            }
            case USER_REPORT -> "INCIDENT_DATA_UNAVAILABLE";
            case SERVICE_PROXIMITY -> "EMERGENCY_FACILITY_DATA_UNAVAILABLE";
            case CONNECTIVITY -> "CONNECTIVITY_DORMANT";
            case OTHER_CONTEXT -> "OTHER_CONTEXT_DORMANT";
        };
    }
}
