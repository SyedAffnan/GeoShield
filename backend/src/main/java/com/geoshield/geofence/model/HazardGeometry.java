package com.geoshield.geofence.model;

import com.geoshield.common.util.GeoDistanceUtil;

import java.util.Objects;

/**
 * Immutable spatial geometry representation of a road hazard for geofence evaluation.
 *
 * <p>This abstraction is decoupled from any specific persistent data storage or table.
 * All test fixtures must set {@code isSynthetic = true} and
 * {@code dataSource = "TEST_FIXTURE_DO_NOT_USE_FOR_NAVIGATION"}.</p>
 */
public final class HazardGeometry {

    public static final double DEFAULT_CORE_RADIUS_METERS = 500.0;
    public static final double DEFAULT_PRE_WARNING_RADIUS_METERS = 1000.0;

    private final String hazardId;
    private final String hazardType;
    private final double latitude;
    private final double longitude;
    private final double coreRadiusMeters;
    private final double preWarningRadiusMeters;
    private final boolean isSynthetic;
    private final String dataSource;
    private final boolean active;

    public HazardGeometry(String hazardId,
                          String hazardType,
                          double latitude,
                          double longitude,
                          double coreRadiusMeters,
                          double preWarningRadiusMeters,
                          boolean isSynthetic,
                          String dataSource,
                          boolean active) {
        this.hazardId = Objects.requireNonNull(hazardId, "hazardId must not be null");
        this.hazardType = hazardType != null ? hazardType : "UNKNOWN";
        GeoDistanceUtil.validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        if (coreRadiusMeters <= 0.0 || preWarningRadiusMeters <= 0.0) {
            throw new IllegalArgumentException("Radii must be positive numbers.");
        }
        if (coreRadiusMeters >= preWarningRadiusMeters) {
            throw new IllegalArgumentException("Core radius must be smaller than pre-warning radius.");
        }
        this.coreRadiusMeters = coreRadiusMeters;
        this.preWarningRadiusMeters = preWarningRadiusMeters;
        this.isSynthetic = isSynthetic;
        this.dataSource = dataSource != null ? dataSource : "UNKNOWN";
        this.active = active;
    }

    public static HazardGeometry ofPoint(String hazardId,
                                         double latitude,
                                         double longitude,
                                         boolean isSynthetic,
                                         String dataSource) {
        return new HazardGeometry(
                hazardId,
                "POINT_HAZARD",
                latitude,
                longitude,
                DEFAULT_CORE_RADIUS_METERS,
                DEFAULT_PRE_WARNING_RADIUS_METERS,
                isSynthetic,
                dataSource,
                true
        );
    }

    public String getHazardId() {
        return hazardId;
    }

    public String getHazardType() {
        return hazardType;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getCoreRadiusMeters() {
        return coreRadiusMeters;
    }

    public double getPreWarningRadiusMeters() {
        return preWarningRadiusMeters;
    }

    public boolean isSynthetic() {
        return isSynthetic;
    }

    public String getDataSource() {
        return dataSource;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HazardGeometry that = (HazardGeometry) o;
        return Double.compare(that.latitude, latitude) == 0 &&
                Double.compare(that.longitude, longitude) == 0 &&
                Double.compare(that.coreRadiusMeters, coreRadiusMeters) == 0 &&
                Double.compare(that.preWarningRadiusMeters, preWarningRadiusMeters) == 0 &&
                isSynthetic == that.isSynthetic &&
                active == that.active &&
                hazardId.equals(that.hazardId) &&
                hazardType.equals(that.hazardType) &&
                dataSource.equals(that.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hazardId, hazardType, latitude, longitude, coreRadiusMeters, preWarningRadiusMeters, isSynthetic, dataSource, active);
    }

    @Override
    public String toString() {
        return "HazardGeometry{" +
                "hazardId='" + hazardId + '\'' +
                ", hazardType='" + hazardType + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", coreRadiusMeters=" + coreRadiusMeters +
                ", preWarningRadiusMeters=" + preWarningRadiusMeters +
                ", isSynthetic=" + isSynthetic +
                ", dataSource='" + dataSource + '\'' +
                ", active=" + active +
                '}';
    }
}
