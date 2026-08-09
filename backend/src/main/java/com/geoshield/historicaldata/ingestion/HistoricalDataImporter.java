package com.geoshield.historicaldata.ingestion;

import java.nio.file.Path;
import java.util.List;

public interface HistoricalDataImporter {
    HistoricalDataset dataset();
    List<HistoricalSafetyRecordDraft> read(Path sourceFile);
}
