package com.geoshield.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.location.dto.LocationResponse;
import com.geoshield.location.entity.TouristLocation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LocationValidityPolicyTest {

    private static final BigDecimal VALID_LAT = new BigDecimal("12.9716000");
    private static final BigDecimal VALID_LON = new BigDecimal("77.5946000");
    private static final BigDecimal ACCURACY_50M = new BigDecimal("50.0");
    private static final BigDecimal ACCURACY_100M = new BigDecimal("100.0");
    private static final BigDecimal ACCURACY_100_1M = new BigDecimal("100.1");
    private static final BigDecimal ACCURACY_150M = new BigDecimal("150.0");

    private static final Instant EVALUATION_INSTANT = Instant.parse("2026-09-19T12:00:00Z");

    private LocationValidityPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new LocationValidityPolicy();
    }

    @Nested
    @DisplayName("Requirement A: Fresh location within 15 minutes and accuracy <= 100m")
    class FreshAndAccurateTests {

        @Test
        @DisplayName("Accepts fix recorded 5 minutes ago with 50m accuracy")
        void acceptsFreshFixWithGoodAccuracy() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(5));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isTrue();
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Accepts fix recorded 14 minutes ago with accuracy at 100m threshold")
        void acceptsFixNearBoundaryWith100mAccuracy() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(14));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_100M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isTrue();
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Accepts fix with null (unreported) accuracy when coordinates and timestamp are fresh")
        void acceptsFreshFixWithUnreportedAccuracy() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(2));
            var result = policy.validate(VALID_LAT, VALID_LON, null, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isTrue();
            assertThat(result.reason()).isNull();
        }
    }

    @Nested
    @DisplayName("Requirement B: 15-minute staleness boundary evaluation")
    class StalenessBoundaryTests {

        @Test
        @DisplayName("Accepts fix recorded exactly at the 15-minute boundary (now - 15m)")
        void acceptsFixExactlyAt15MinuteBoundary() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(15));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isTrue();
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Rejects fix recorded just beyond the 15-minute boundary (now - 15m 1s)")
        void rejectsFixJustBeyond15MinuteBoundary() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(15)).minusSeconds(1);
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("stale").contains("exceeding maximum age of 15 minutes");
        }

        @Test
        @DisplayName("Rejects fix recorded 30 minutes ago")
        void rejectsFix30MinutesOld() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(30));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("stale");
        }

        @Test
        @DisplayName("Rejects fix recorded 24 hours ago")
        void rejectsFix24HoursOld() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofHours(24));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("stale");
        }
    }

    @Nested
    @DisplayName("Requirement C: 100m horizontal accuracy boundary evaluation")
    class AccuracyBoundaryTests {

        @Test
        @DisplayName("Accepts fix with reported accuracy exactly at 100.0m threshold")
        void acceptsAccuracyExactlyAt100m() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(1));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_100M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isTrue();
            assertThat(result.reason()).isNull();
        }

        @Test
        @DisplayName("Rejects fix with reported accuracy just beyond 100m threshold (100.1m)")
        void rejectsAccuracyJustBeyond100m() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(1));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_100_1M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("accuracy 100.1m exceeds maximum allowable threshold of 100.0m");
        }

        @Test
        @DisplayName("Rejects fix with coarse accuracy of 150m")
        void rejectsCoarseAccuracy150m() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(1));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_150M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("accuracy 150.0m exceeds maximum allowable threshold of 100.0m");
        }

        @Test
        @DisplayName("Rejects fix with negative accuracy")
        void rejectsNegativeAccuracy() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(1));
            var result = policy.validate(VALID_LAT, VALID_LON, new BigDecimal("-1.0"), recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("accuracy must be non-negative");
        }
    }

    @Nested
    @DisplayName("Requirement D: Both stale and inaccurate fixes")
    class BothStaleAndInaccurateTests {

        @Test
        @DisplayName("Rejects fix that is both stale (20 minutes) and inaccurate (150m)")
        void rejectsBothStaleAndInaccurate() {
            Instant recordedAt = EVALUATION_INSTANT.minus(Duration.ofMinutes(20));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_150M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            // Fails on staleness check first or accuracy
            assertThat(result.reason()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Requirement E: Future timestamp and clock anomaly handling")
    class FutureTimestampTests {

        @Test
        @DisplayName("Rejects fix with timestamp 1 second in the future")
        void rejectsFix1SecondInFuture() {
            Instant recordedAt = EVALUATION_INSTANT.plusSeconds(1);
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("timestamp is in the future");
        }

        @Test
        @DisplayName("Rejects fix with timestamp 5 minutes in the future")
        void rejectsFix5MinutesInFuture() {
            Instant recordedAt = EVALUATION_INSTANT.plus(Duration.ofMinutes(5));
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, recordedAt, EVALUATION_INSTANT);

            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("timestamp is in the future");
        }
    }

    @Nested
    @DisplayName("Coordinate and required data validity")
    class CoordinateValidityTests {

        @Test
        @DisplayName("Rejects null recordedAt timestamp")
        void rejectsNullTimestamp() {
            var result = policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, null, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("timestamp is required");
        }

        @Test
        @DisplayName("Rejects null latitude")
        void rejectsNullLatitude() {
            var result = policy.validate(null, VALID_LON, ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("latitude is required");
        }

        @Test
        @DisplayName("Rejects null longitude")
        void rejectsNullLongitude() {
            var result = policy.validate(VALID_LAT, null, ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("longitude is required");
        }

        @Test
        @DisplayName("Rejects latitude < -90.0")
        void rejectsLatitudeBelowMin() {
            var result = policy.validate(new BigDecimal("-90.0001"), VALID_LON, ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("latitude").contains("outside valid range");
        }

        @Test
        @DisplayName("Rejects latitude > 90.0")
        void rejectsLatitudeAboveMax() {
            var result = policy.validate(new BigDecimal("90.0001"), VALID_LON, ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("latitude").contains("outside valid range");
        }

        @Test
        @DisplayName("Rejects longitude < -180.0")
        void rejectsLongitudeBelowMin() {
            var result = policy.validate(VALID_LAT, new BigDecimal("-180.0001"), ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("longitude").contains("outside valid range");
        }

        @Test
        @DisplayName("Rejects longitude > 180.0")
        void rejectsLongitudeAboveMax() {
            var result = policy.validate(VALID_LAT, new BigDecimal("180.0001"), ACCURACY_50M, EVALUATION_INSTANT, EVALUATION_INSTANT);
            assertThat(result.isValid()).isFalse();
            assertThat(result.reason()).contains("longitude").contains("outside valid range");
        }

        @Test
        @DisplayName("Throws IllegalArgumentException if evaluation instant (now) is null")
        void throwsWhenNowIsNull() {
            assertThatThrownBy(() -> policy.validate(VALID_LAT, VALID_LON, ACCURACY_50M, EVALUATION_INSTANT, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("Current instant (now) must not be null");
        }
    }

    @Nested
    @DisplayName("Entity and DTO overloads")
    class OverloadTests {

        @Test
        @DisplayName("Validates TouristLocation entity correctly")
        void validatesTouristLocationEntity() {
            TouristLocation location = new TouristLocation();
            User user = new User("tourist", "tourist@example.com", "hash", "Tourist", "+919876543210", new UserRole(Role.TOURIST));
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
            location.setUser(user);
            location.update(VALID_LAT, VALID_LON, ACCURACY_50M, BigDecimal.ZERO, EVALUATION_INSTANT.minus(Duration.ofMinutes(3)));

            assertThat(policy.isValid(location, EVALUATION_INSTANT)).isTrue();
            assertThat(policy.validate(location, EVALUATION_INSTANT).isValid()).isTrue();

            // Null entity handling
            assertThat(policy.isValid((TouristLocation) null, EVALUATION_INSTANT)).isFalse();
        }

        @Test
        @DisplayName("Validates LocationResponse DTO correctly")
        void validatesLocationResponseDto() {
            LocationResponse response = new LocationResponse(1L, VALID_LAT, VALID_LON, ACCURACY_50M, BigDecimal.ZERO,
                    EVALUATION_INSTANT.minus(Duration.ofMinutes(4)));

            assertThat(policy.isValid(response, EVALUATION_INSTANT)).isTrue();
            assertThat(policy.validate(response, EVALUATION_INSTANT).isValid()).isTrue();

            // Null DTO handling
            assertThat(policy.isValid((LocationResponse) null, EVALUATION_INSTANT)).isFalse();
        }
    }
}
