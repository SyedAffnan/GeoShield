package com.geoshield.risk.dto;

import java.util.List;

public record RiskFeatureVector(List<NormalizedRiskFeature> features) {
    public NormalizedRiskFeature feature(RiskFactorType type) {
        return features.stream().filter(feature -> feature.factor() == type).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing risk feature: " + type));
    }
}
