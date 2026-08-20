package com.geoshield.historicaldata.repository;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.entity.HistoricalSourceType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricalSafetyRecordRepository extends JpaRepository<HistoricalSafetyRecord, Long> {
    boolean existsBySourceType(HistoricalSourceType sourceType);
    java.util.List<HistoricalSafetyRecord> findAllByGeographicLevel(GeographicLevel geographicLevel);

    Optional<HistoricalSafetyRecord> findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
            String source, int sourceYear, GeographicLevel geographicLevel, String geographicUnit, String category,
            String metricName);
}
