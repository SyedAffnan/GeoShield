package com.geoshield.historicaldata.entity;

import com.geoshield.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name = "historical_safety_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_historical_safety_record_natural", columnNames = {
                "source", "source_year", "geographic_level", "geographic_unit", "category", "metric_name"}),
        indexes = {
                @Index(name = "idx_historical_safety_source_year", columnList = "source,source_year"),
                @Index(name = "idx_historical_safety_geography", columnList = "geographic_level,geographic_unit"),
                @Index(name = "idx_historical_safety_category", columnList = "category")
        })
public class HistoricalSafetyRecord extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id", nullable = false, updatable = false)
    private Long id;

    @Column(nullable = false, length = 128)
    private String source;

    @Column(name = "source_year", nullable = false)
    private int sourceYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private HistoricalSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "geographic_level", nullable = false, length = 16)
    private GeographicLevel geographicLevel;

    @Column(name = "geographic_unit", nullable = false, length = 128)
    private String geographicUnit;

    @Column(nullable = false, length = 200)
    private String category;

    @Column(name = "metric_name", nullable = false, length = 200)
    private String metricName;

    @Column(name = "metric_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal metricValue;

    @Column(name = "tourist_specific", nullable = false)
    private boolean touristSpecific;

    protected HistoricalSafetyRecord() { }

    public HistoricalSafetyRecord(String source, int sourceYear, GeographicLevel geographicLevel, String geographicUnit,
            String category, String metricName, BigDecimal metricValue, boolean touristSpecific) {
        this.source = source;
        this.sourceYear = sourceYear;
        this.sourceType = HistoricalSourceType.HISTORICAL;
        this.geographicLevel = geographicLevel;
        this.geographicUnit = geographicUnit;
        this.category = category;
        this.metricName = metricName;
        this.metricValue = metricValue;
        this.touristSpecific = touristSpecific;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public int getSourceYear() { return sourceYear; }
    public HistoricalSourceType getSourceType() { return sourceType; }
    public GeographicLevel getGeographicLevel() { return geographicLevel; }
    public String getGeographicUnit() { return geographicUnit; }
    public String getCategory() { return category; }
    public String getMetricName() { return metricName; }
    public BigDecimal getMetricValue() { return metricValue; }
    public boolean isTouristSpecific() { return touristSpecific; }
}
