package com.geoshield.historicaldata.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import java.nio.file.Path;

public interface HistoricalDataService extends ModuleService {
    HistoricalDataImportResult importDataset(HistoricalDataset dataset, Path sourceFile);
    boolean hasHistoricalSafetyRecords();

    // TODO(architecture-open): add approved geographically resolved risk-feature query contracts.
}
