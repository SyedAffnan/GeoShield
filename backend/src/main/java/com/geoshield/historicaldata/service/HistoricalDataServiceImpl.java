package com.geoshield.historicaldata.service;

import com.geoshield.common.exception.ValidationException;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.entity.HistoricalSourceType;
import com.geoshield.historicaldata.ingestion.HistoricalDataImporter;
import com.geoshield.historicaldata.ingestion.HistoricalDataset;
import com.geoshield.historicaldata.ingestion.HistoricalSafetyRecordDraft;
import com.geoshield.historicaldata.repository.HistoricalSafetyRecordRepository;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoricalDataServiceImpl implements HistoricalDataService {
    private final HistoricalSafetyRecordRepository repository;
    private final HistoricalSafetyRecordValidator validator;
    private final Map<HistoricalDataset, HistoricalDataImporter> importers;

    public HistoricalDataServiceImpl(HistoricalSafetyRecordRepository repository, HistoricalSafetyRecordValidator validator,
            List<HistoricalDataImporter> importers) {
        this.repository = repository;
        this.validator = validator;
        this.importers = new EnumMap<>(HistoricalDataset.class);
        importers.forEach(importer -> this.importers.put(importer.dataset(), importer));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasHistoricalSafetyRecords() {
        return repository.existsBySourceType(HistoricalSourceType.HISTORICAL);
    }

    @Override
    @Transactional
    public HistoricalDataImportResult importDataset(HistoricalDataset dataset, Path sourceFile) {
        HistoricalDataImporter importer = importers.get(dataset);
        if (importer == null) {
            throw new ValidationException("No importer is registered for dataset: " + dataset);
        }
        List<HistoricalSafetyRecordDraft> drafts = importer.read(sourceFile);
        int imported = 0;
        int skipped = 0;
        for (HistoricalSafetyRecordDraft draft : drafts) {
            validator.validate(dataset, draft);
            boolean exists = repository.findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
                    draft.source(), draft.sourceYear(), draft.geographicLevel(), draft.geographicUnit(), draft.category(),
                    draft.metricName()).isPresent();
            if (exists) {
                skipped++;
                continue;
            }
            repository.save(new HistoricalSafetyRecord(draft.source(), draft.sourceYear(), draft.geographicLevel(),
                    draft.geographicUnit(), draft.category(), draft.metricName(), draft.metricValue(), draft.touristSpecific()));
            imported++;
        }
        return new HistoricalDataImportResult(dataset.source(), drafts.size(), imported, skipped);
    }
}
