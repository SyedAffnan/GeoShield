package com.geoshield.risk.dto;

import java.util.Map;

/**
 * Provenance metadata for the 2024 retrospective temporal-holdout evaluation.
 */
public record ModelProvenanceDto(
        String experimentIdentifier,
        String predictionSource,
        String advisoryReleaseVersion,
        String modelArtifact,
        String modelArtifactStatus,
        String datasetIdentifier,
        String datasetHashSha256,
        String artifactGeneratedAt,
        String trainingTimestamp,
        String trainingTimestampStatus,
        String trainingPeriod,
        Integer trainingRows,
        Integer holdoutYear,
        Integer evaluationRows,
        Map<String, Object> evaluationMetrics,
        Map<String, Object> modelConfiguration,
        String limitations
) {
}
