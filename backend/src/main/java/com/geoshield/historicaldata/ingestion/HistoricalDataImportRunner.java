package com.geoshield.historicaldata.ingestion;

import com.geoshield.config.HistoricalDataIngestionProperties;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.service.HistoricalDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class HistoricalDataImportRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(HistoricalDataImportRunner.class);

    private final HistoricalDataIngestionProperties properties;
    private final HistoricalDataService historicalDataService;

    HistoricalDataImportRunner(HistoricalDataIngestionProperties properties, HistoricalDataService historicalDataService) {
        this.properties = properties;
        this.historicalDataService = historicalDataService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        if (properties.getMorthSource() == null || properties.getNcrbSource() == null) {
            throw new IllegalStateException("Both MoRTH and NCRB source paths are required when historical ingestion is enabled");
        }
        log(historicalDataService.importDataset(HistoricalDataset.MORTH_ROAD_ACCIDENTS_2024, properties.getMorthSource()));
        log(historicalDataService.importDataset(HistoricalDataset.NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2, properties.getNcrbSource()));
    }

    private void log(HistoricalDataImportResult result) {
        LOGGER.info("Historical dataset import completed: source={}, read={}, imported={}, skipped={}", result.source(),
                result.recordsRead(), result.recordsImported(), result.recordsSkipped());
    }
}
