package com.geoshield.historicaldata.repository;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.historicaldata.entity.HistoricalSafetyRecord;
import com.geoshield.historicaldata.entity.HistoricalSourceType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricalSafetyRecordRepository extends JpaRepository<HistoricalSafetyRecord, Long> {
    boolean existsBySourceType(HistoricalSourceType sourceType);
    List<HistoricalSafetyRecord> findAllByGeographicLevel(GeographicLevel geographicLevel);
    List<HistoricalSafetyRecord> findAllByGeographicLevelAndParentUnit(GeographicLevel geographicLevel, String parentUnit);

    Optional<HistoricalSafetyRecord> findBySourceAndSourceYearAndGeographicLevelAndParentUnitAndGeographicUnitAndCategoryAndMetricName(
            String source, int sourceYear, GeographicLevel geographicLevel, String parentUnit, String geographicUnit,
            String category, String metricName);

    default Optional<HistoricalSafetyRecord> findBySourceAndSourceYearAndGeographicLevelAndGeographicUnitAndCategoryAndMetricName(
            String source, int sourceYear, GeographicLevel geographicLevel, String geographicUnit, String category,
            String metricName) {
        return findBySourceAndSourceYearAndGeographicLevelAndParentUnitAndGeographicUnitAndCategoryAndMetricName(
                source, sourceYear, geographicLevel, "India", geographicUnit, category, metricName);
    }

    Optional<HistoricalSafetyRecord> findBySourceAndSourceYearAndGeographicLevelAndParentUnitAndGeographicUnitAndCategoryAndMetricNameAndTouristSpecific(
            String source, int sourceYear, GeographicLevel geographicLevel, String parentUnit, String geographicUnit,
            String category, String metricName, boolean touristSpecific);

    @Query("SELECT r FROM HistoricalSafetyRecord r WHERE r.source = :source " +
           "AND r.sourceYear = :sourceYear " +
           "AND r.geographicLevel = :level " +
           "AND LOWER(r.parentUnit) = LOWER(:parentUnit) " +
           "AND LOWER(r.geographicUnit) = LOWER(:unit) " +
           "AND r.metricName LIKE %:metricNamePattern% " +
           "AND r.touristSpecific = false " +
           "ORDER BY r.id ASC")
    List<HistoricalSafetyRecord> findSpecificMetrics(
            @Param("source") String source,
            @Param("sourceYear") int sourceYear,
            @Param("level") GeographicLevel level,
            @Param("parentUnit") String parentUnit,
            @Param("unit") String unit,
            @Param("metricNamePattern") String metricNamePattern);

    default Optional<HistoricalSafetyRecord> findSpecificMetric(
            String source,
            int sourceYear,
            GeographicLevel level,
            String parentUnit,
            String unit,
            String metricNamePattern) {
        List<HistoricalSafetyRecord> matches = findSpecificMetrics(
                source, sourceYear, level, parentUnit, unit, metricNamePattern);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        for (HistoricalSafetyRecord r : matches) {
            if (r.getMetricName().equalsIgnoreCase(metricNamePattern)) {
                return Optional.of(r);
            }
        }
        return Optional.of(matches.get(0));
    }
}
