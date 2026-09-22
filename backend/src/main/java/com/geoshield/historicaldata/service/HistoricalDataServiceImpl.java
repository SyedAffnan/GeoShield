package com.geoshield.historicaldata.service;

import com.geoshield.common.exception.ValidationException;
import com.geoshield.historicaldata.dto.HistoricalDataImportResult;
import com.geoshield.historicaldata.dto.HistoricalSafetyRecordSummary;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.entity.HistoricalSourceType;
import com.geoshield.historicaldata.entity.GeographicLevel;
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

import com.geoshield.historicaldata.dto.HistoricalMetricResult;
import java.util.Optional;

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
    @Transactional(readOnly = true)
    public List<HistoricalSafetyRecordSummary> getHistoricalSafetyRecords(GeographicLevel geographicLevel) {
        return repository.findAllByGeographicLevel(geographicLevel).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoricalSafetyRecordSummary> getHistoricalSafetyRecords(GeographicLevel geographicLevel, String parentUnit) {
        return repository.findAllByGeographicLevelAndParentUnit(geographicLevel, parentUnit).stream().map(this::toSummary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HistoricalMetricResult getSafetyMetricWithFallback(
            GeographicLevel targetLevel,
            String parentUnit,
            String targetUnit,
            String source,
            int sourceYear,
            String metricNamePattern) {
        if (targetLevel == GeographicLevel.DISTRICT) {
            if (parentUnit == null || parentUnit.isBlank()) {
                return HistoricalMetricResult.unavailable("NO_PARENT_STATE");
            }
            if (targetUnit == null || targetUnit.isBlank()) {
                return fallbackToState(parentUnit, source, sourceYear, metricNamePattern, "MISSING_DISTRICT_MAPPING");
            }

            Optional<HistoricalSafetyRecord> exactDistrict = repository.findSpecificMetric(
                    source, sourceYear, GeographicLevel.DISTRICT, parentUnit, targetUnit, metricNamePattern);
            if (exactDistrict.isPresent()) {
                HistoricalSafetyRecord rec = exactDistrict.get();
                if (rec.getMetricValue() == null || rec.getMetricValue().signum() <= 0) {
                    return fallbackToState(parentUnit, source, sourceYear, metricNamePattern, "INVALID_DISTRICT_METRIC");
                }
                return HistoricalMetricResult.direct(toSummary(rec));
            }

            // Check if district record exists under a different source year or invalid metric
            List<HistoricalSafetyRecord> districtRecords = repository.findAllByGeographicLevelAndParentUnit(
                    GeographicLevel.DISTRICT, parentUnit);
            boolean hasDifferentYear = districtRecords.stream()
                    .anyMatch(r -> targetUnit.equalsIgnoreCase(r.getGeographicUnit())
                            && source.equals(r.getSource())
                            && r.getMetricName().contains(metricNamePattern)
                            && r.getSourceYear() != sourceYear);
            if (hasDifferentYear) {
                return fallbackToState(parentUnit, source, sourceYear, metricNamePattern, "UNSUPPORTED_DISTRICT_YEAR");
            }

            return fallbackToState(parentUnit, source, sourceYear, metricNamePattern, "NO_DISTRICT_RECORD");
        }

        if (targetLevel == GeographicLevel.STATE_UT) {
            Optional<HistoricalSafetyRecord> stateMatch = repository.findSpecificMetric(
                    source, sourceYear, GeographicLevel.STATE_UT, "India", targetUnit, metricNamePattern);
            return stateMatch.map(r -> HistoricalMetricResult.direct(toSummary(r)))
                    .orElseGet(() -> HistoricalMetricResult.unavailable("STATE_METRIC_NOT_FOUND"));
        }

        return HistoricalMetricResult.unavailable("Unsupported geographic level: " + targetLevel);
    }

    private HistoricalMetricResult fallbackToState(String parentState, String source, int sourceYear,
            String metricNamePattern, String fallbackReason) {
        Optional<HistoricalSafetyRecord> stateMatch = repository.findSpecificMetric(
                source, sourceYear, GeographicLevel.STATE_UT, "India", parentState, metricNamePattern);
        return stateMatch.map(r -> HistoricalMetricResult.fallback(toSummary(r), fallbackReason))
                .orElseGet(() -> HistoricalMetricResult.unavailable(fallbackReason));
    }

    private HistoricalSafetyRecordSummary toSummary(HistoricalSafetyRecord record) {
        return new HistoricalSafetyRecordSummary(
                record.getSource(), record.getSourceYear(), record.getGeographicLevel(),
                record.getParentUnit(), record.getGeographicUnit(), record.getCategory(),
                record.getMetricName(), record.getMetricValue(), record.isTouristSpecific());
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
            boolean exists = repository.findBySourceAndSourceYearAndGeographicLevelAndParentUnitAndGeographicUnitAndCategoryAndMetricName(
                    draft.source(), draft.sourceYear(), draft.geographicLevel(), draft.parentUnit(), draft.geographicUnit(),
                    draft.category(), draft.metricName()).isPresent();
            if (exists) {
                skipped++;
                continue;
            }
            repository.save(new HistoricalSafetyRecord(draft.source(), draft.sourceYear(), draft.geographicLevel(),
                    draft.parentUnit(), draft.geographicUnit(), draft.category(), draft.metricName(), draft.metricValue(),
                    draft.touristSpecific()));
            imported++;
        }
        return new HistoricalDataImportResult(dataset.source(), drafts.size(), imported, skipped);
    }
}
