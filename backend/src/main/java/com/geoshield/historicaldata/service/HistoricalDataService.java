package com.geoshield.historicaldata.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import java.nio.file.Path;

public interface HistoricalDataService extends ModuleService {
    HistoricalDataImportResult importDataset(HistoricalDataset dataset, Path sourceFile);
    boolean hasHistoricalSafetyRecords();
    java.util.List<HistoricalSafetyRecordSummary> getHistoricalSafetyRecords(GeographicLevel geographicLevel);

    // TODO(architecture-open): add approved geographically resolved risk-feature query contracts.
}
