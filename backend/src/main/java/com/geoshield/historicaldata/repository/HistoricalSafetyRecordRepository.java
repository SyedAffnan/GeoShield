package com.geoshield.historicaldata.repository;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricalSafetyRecordRepository extends JpaRepository<HistoricalSafetyRecord, Long> {
    Optional<HistoricalSafetyRecord> findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
            String source, int sourceYear, GeographicLevel geographicLevel, String geographicUnit, String category,
            String metricName);
}
