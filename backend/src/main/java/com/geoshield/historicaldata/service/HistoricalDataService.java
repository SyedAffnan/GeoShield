package com.geoshield.historicaldata.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import java.nio.file.Path;

import com.geoshield.historicaldata.dto.HistoricalMetricResult;
import java.util.List;

public interface HistoricalDataService extends ModuleService {
    HistoricalDataImportResult importDataset(HistoricalDataset dataset, Path sourceFile);
    boolean hasHistoricalSafetyRecords();
    List<HistoricalSafetyRecordSummary> getHistoricalSafetyRecords(GeographicLevel geographicLevel);
    List<HistoricalSafetyRecordSummary> getHistoricalSafetyRecords(GeographicLevel geographicLevel, String parentUnit);

    HistoricalMetricResult getSafetyMetricWithFallback(
            GeographicLevel targetLevel,
            String parentUnit,
            String targetUnit,
            String source,
            int sourceYear,
            String metricNamePattern);
}
