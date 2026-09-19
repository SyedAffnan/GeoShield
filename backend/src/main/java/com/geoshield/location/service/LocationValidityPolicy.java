package com.geoshield.location.service;

import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.entity.TouristLocation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Policy governing the temporal freshness and spatial accuracy of tourist location fixes
 * for current-location retrieval and downstream safety risk evaluation.
 *
 * <p>Approved engineering starting policy parameters for GeoShield Step 6:
 * <ul>
 *   <li>Maximum location age: 15 minutes</li>
 *   <li>Horizontal accuracy threshold: 100 metres</li>
 * </ul>
 *
 * <p>Note: These parameters represent the approved engineering starting baseline from the
 * Step 6 prerequisite design, not universally validated empirical field measurements.
 * They establish the project's current operational thresholds for distinguishing valid
 * real-time fixes from stale or coarse positioning data.
 */
@org.springframework.stereotype.Component
public class LocationValidityPolicy {

    /** Maximum allowable age of a GPS fix to be considered current and valid (15 minutes). */
    public static final Duration MAX_LOCATION_AGE = Duration.ofMinutes(15);

    /** Maximum allowable horizontal accuracy radius in metres (100 metres). */
    public static final BigDecimal MAX_HORIZONTAL_ACCURACY_METERS = new BigDecimal("100.0");

    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90.0");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90.0");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180.0");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180.0");

    /**
     * Evaluates whether a location fix satisfies freshness, accuracy, and data validity constraints.
     *
     * @param latitude horizontal coordinate in degrees [-90.0, 90.0]
     * @param longitude vertical coordinate in degrees [-180.0, 180.0]
     * @param accuracy horizontal accuracy radius in metres, or null if unreported
     * @param recordedAt timestamp when the fix was recorded on device/client
     * @param now current evaluation instant
     * @return {@link LocationValidityResult} detailing whether the fix is valid, with diagnostic reason if not
     */
    public LocationValidityResult validate(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy,
            Instant recordedAt, Instant now) {
        Objects.requireNonNull(now, "Current instant (now) must not be null");

        if (recordedAt == null) {
            return LocationValidityResult.invalid("Location timestamp is required");
        }

        if (latitude == null) {
            return LocationValidityResult.invalid("Location latitude is required");
        }

        if (longitude == null) {
            return LocationValidityResult.invalid("Location longitude is required");
        }

        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            return LocationValidityResult.invalid(
                    "Location latitude " + latitude + " is outside valid range [-90.0, 90.0]");
        }

        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            return LocationValidityResult.invalid(
                    "Location longitude " + longitude + " is outside valid range [-180.0, 180.0]");
        }

        // Clock anomaly protection: future timestamps are rejected rather than appearing artificially fresh
        if (recordedAt.isAfter(now)) {
            return LocationValidityResult.invalid(
                    "Location timestamp is in the future: recorded_at " + recordedAt + " is after evaluation time " + now);
        }

        // Staleness evaluation: fixes older than 15 minutes are stale
        Instant oldestAllowed = now.minus(MAX_LOCATION_AGE);
        if (recordedAt.isBefore(oldestAllowed)) {
            long ageSeconds = Duration.between(recordedAt, now).toSeconds();
            return LocationValidityResult.invalid(
                    "Location is stale: recorded_at " + recordedAt + " is " + ageSeconds + "s old, exceeding maximum age of "
                            + MAX_LOCATION_AGE.toMinutes() + " minutes");
        }

        // Accuracy evaluation: reported accuracy must be non-negative and <= 100 metres
        if (accuracy != null) {
            if (accuracy.compareTo(BigDecimal.ZERO) < 0) {
                return LocationValidityResult.invalid(
                        "Location accuracy must be non-negative: " + accuracy);
            }
            if (accuracy.compareTo(MAX_HORIZONTAL_ACCURACY_METERS) > 0) {
                return LocationValidityResult.invalid(
                        "Location accuracy " + accuracy + "m exceeds maximum allowable threshold of "
                                + MAX_HORIZONTAL_ACCURACY_METERS + "m");
            }
        }

        return LocationValidityResult.valid();
    }

    /** Validates a {@link TouristLocation} entity against the current instant. */
    public LocationValidityResult validate(TouristLocation location, Instant now) {
        if (location == null) {
            return LocationValidityResult.invalid("Tourist location entity is null");
        }
        return validate(location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getRecordedAt(), now);
    }

    /** Validates a {@link LocationResponse} DTO against the current instant. */
    public LocationValidityResult validate(LocationResponse response, Instant now) {
        if (response == null) {
            return LocationValidityResult.invalid("Location response is null");
        }
        return validate(response.latitude(), response.longitude(), response.accuracy(), response.timestamp(), now);
    }

    /** Convenience predicate for entity validity. */
    public boolean isValid(TouristLocation location, Instant now) {
        return validate(location, now).isValid();
    }

    /** Convenience predicate for DTO validity. */
    public boolean isValid(LocationResponse response, Instant now) {
        return validate(response, now).isValid();
    }

    /** Result of location validity evaluation with explanation. */
    public record LocationValidityResult(boolean isValid, String reason) {
        public static LocationValidityResult valid() {
            return new LocationValidityResult(true, null);
        }

        public static LocationValidityResult invalid(String reason) {
            return new LocationValidityResult(false, reason);
        }
    }
}
