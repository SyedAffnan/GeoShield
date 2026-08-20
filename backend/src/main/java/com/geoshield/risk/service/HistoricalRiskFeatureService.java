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
    private static final String MORTH_SOURCE = "MoRTH Road Accidents in India 2024";

    private final HistoricalDataService historicalDataService;
    public HistoricalRiskFeatureService(HistoricalDataService historicalDataService) { this.historicalDataService = historicalDataService; }

    public NormalizedRiskFeature historicalIncidentRisk(GeographicResolution resolution) {
        if (!resolution.resolved() || resolution.geographicLevel() != GeographicLevel.STATE_UT) {
            return NormalizedRiskFeature.unavailable(RiskFactorType.HISTORICAL_INCIDENT, MORTH_SOURCE,
                    resolution.reason() == null ? "A resolved State/UT is required for this feature." : resolution.reason(),
                    "Requires a verified State/UT geographic resolution.");
        }
        List<HistoricalSafetyRecordSummary> records = historicalDataService.getHistoricalSafetyRecords(GeographicLevel.STATE_UT);
        List<HistoricalSafetyRecordSummary> rates = records.stream().filter(record -> MORTH_SOURCE.equals(record.source())
                && !record.touristSpecific() && record.metricName().contains("Per Lakh Population")).toList();
        HistoricalSafetyRecordSummary local = rates.stream().filter(record -> resolution.geographicUnit().equalsIgnoreCase(record.geographicUnit()))
                .findFirst().orElse(null);
        BigDecimal maximum = rates.stream().map(HistoricalSafetyRecordSummary::metricValue).max(BigDecimal::compareTo).orElse(null);
        if (local == null || maximum == null || maximum.signum() == 0) {
            return NormalizedRiskFeature.unavailable(RiskFactorType.HISTORICAL_INCIDENT, MORTH_SOURCE,
                    "No supported State/UT per-lakh road-injury metric is available for the resolved unit.",
                    "Relative maximum normalization is unavailable without a matching metric.");
        }
        BigDecimal normalized = local.metricValue().multiply(BigDecimal.valueOf(100)).divide(maximum, 8, RoundingMode.HALF_UP);
        return new NormalizedRiskFeature(RiskFactorType.HISTORICAL_INCIDENT, normalized, true, MORTH_SOURCE,
                "State/UT-level general road-safety metric; not tourist-specific.",
                "metricValue / maximum same-metric State/UT value × 100");
    }
}
