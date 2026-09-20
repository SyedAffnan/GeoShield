package com.geoshield.geofence.service;

import com.geoshield.common.util.GeoDistanceUtil;
import com.geoshield.geofence.model.GeofenceEvaluationResult;
import com.geoshield.geofence.model.GeofenceZone;
import com.geoshield.geofence.model.HazardGeometry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production implementation of {@link GeofencingService}.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Stateless geofence evaluation with zero database persistence.</li>
 *   <li>Canonical spherical Haversine distance using {@link GeoDistanceUtil}.</li>
 *   <li>Phase 0.2 location validity rules (max age 15 minutes, max accuracy 100 m, strictly non-future).</li>
 *   <li>State-dependent hysteresis where expansion H = max(50.0, 2 * accuracy) applies exclusively to zone exits.</li>
 *   <li>Deterministic evaluation ordering by hazard ID.</li>
 * </ul>
 */
@Service
public class GeofencingServiceImpl implements GeofencingService {

    public static final double MAX_ALLOWABLE_ACCURACY_METERS = 100.0;
    public static final long MAX_LOCATION_AGE_MINUTES = 15L;
    public static final double DEFAULT_HYSTERESIS_METERS = 50.0;

    private final Clock clock;

    public GeofencingServiceImpl() {
        this(Clock.systemUTC());
    }

    public GeofencingServiceImpl(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public GeofenceEvaluationResult evaluate(
            double userLatitude,
            double userLongitude,
            Double reportedAccuracyMeters,
            GeofenceZone previousZone,
            HazardGeometry hazard,
            Instant evaluatedAt) {

        validateLocationFix(userLatitude, userLongitude, reportedAccuracyMeters, evaluatedAt);
        Objects.requireNonNull(previousZone, "previousZone must not be null");
        Objects.requireNonNull(hazard, "hazard must not be null");

        double distanceMeters = GeoDistanceUtil.distanceMeters(
                userLatitude,
                userLongitude,
                hazard.getLatitude(),
                hazard.getLongitude()
        );

        double accuracy = reportedAccuracyMeters != null ? reportedAccuracyMeters : DEFAULT_HYSTERESIS_METERS / 2.0;
        double h = Math.max(DEFAULT_HYSTERESIS_METERS, 2.0 * accuracy);

        double coreRadius = hazard.getCoreRadiusMeters();
        double preWarningRadius = hazard.getPreWarningRadiusMeters();
        double rCoreExit = coreRadius + h;
        double rPreExit = preWarningRadius + h;

        GeofenceZone nextZone = computeNextZone(previousZone, distanceMeters, coreRadius, preWarningRadius, rCoreExit, rPreExit);
        boolean transitionOccurred = previousZone != nextZone;

        return new GeofenceEvaluationResult(
                hazard.getHazardId(),
                previousZone,
                nextZone,
                distanceMeters,
                transitionOccurred,
                evaluatedAt
        );
    }

    @Override
    public List<GeofenceEvaluationResult> evaluateAll(
            double userLatitude,
            double userLongitude,
            Double reportedAccuracyMeters,
            Map<String, GeofenceZone> previousZones,
            List<HazardGeometry> hazards,
            Instant evaluatedAt) {

        if (hazards == null || hazards.isEmpty()) {
            return List.of();
        }

        // Deterministic processing order by hazard ID
        List<HazardGeometry> sortedHazards = new ArrayList<>(hazards);
        sortedHazards.sort(Comparator.comparing(HazardGeometry::getHazardId));

        List<GeofenceEvaluationResult> results = new ArrayList<>(sortedHazards.size());
        for (HazardGeometry hazard : sortedHazards) {
            GeofenceZone prev = (previousZones != null && previousZones.containsKey(hazard.getHazardId()))
                    ? previousZones.get(hazard.getHazardId())
                    : GeofenceZone.OUTSIDE;
            results.add(evaluate(userLatitude, userLongitude, reportedAccuracyMeters, prev, hazard, evaluatedAt));
        }

        return results;
    }

    /**
     * Computes next zone adhering to the canonical state-dependent hysteresis specification.
     */
    private GeofenceZone computeNextZone(GeofenceZone currentZone,
                                         double d,
                                         double coreRadius,
                                         double preWarningRadius,
                                         double rCoreExit,
                                         double rPreExit) {
        return switch (currentZone) {
            case OUTSIDE -> {
                if (d <= coreRadius) {
                    yield GeofenceZone.CORE;
                } else if (d <= preWarningRadius) {
                    yield GeofenceZone.PRE_WARNING;
                } else {
                    yield GeofenceZone.OUTSIDE;
                }
            }
            case PRE_WARNING -> {
                if (d <= coreRadius) {
                    yield GeofenceZone.CORE; // Strict entry, H never expands entry
                } else if (d > rPreExit) {
                    yield GeofenceZone.OUTSIDE; // Exit using hysteresis
                } else {
                    yield GeofenceZone.PRE_WARNING;
                }
            }
            case CORE -> {
                if (d > rPreExit) {
                    yield GeofenceZone.OUTSIDE; // Direct exit
                } else if (d > rCoreExit) {
                    yield GeofenceZone.PRE_WARNING; // Exit using hysteresis
                } else {
                    yield GeofenceZone.CORE;
                }
            }
        };
    }

    /**
     * Validates that the location fix complies with Phase 0.2 validity requirements.
     */
    private void validateLocationFix(double lat, double lon, Double accuracy, Instant evaluatedAt) {
        GeoDistanceUtil.validateCoordinates(lat, lon);

        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");

        Instant now = clock.instant();
        // Clock anomaly protection: future timestamps are strictly rejected per Phase 0.2 (no future leeway)
        if (evaluatedAt.isAfter(now)) {
            throw new IllegalArgumentException("Location timestamp is in the future: " + evaluatedAt);
        }

        // Staleness evaluation: fixes older than 15 minutes are stale per Phase 0.2
        Instant oldestAllowed = now.minus(Duration.ofMinutes(MAX_LOCATION_AGE_MINUTES));
        if (evaluatedAt.isBefore(oldestAllowed)) {
            throw new IllegalArgumentException("Location fix is stale (older than " + MAX_LOCATION_AGE_MINUTES + " minutes): " + evaluatedAt);
        }

        // Accuracy evaluation: reported accuracy must be non-negative and <= 100 metres per Phase 0.2
        if (accuracy != null) {
            if (Double.isNaN(accuracy) || Double.isInfinite(accuracy) || accuracy < 0.0) {
                throw new IllegalArgumentException("Invalid GPS accuracy: " + accuracy);
            }
            if (accuracy > MAX_ALLOWABLE_ACCURACY_METERS) {
                throw new IllegalArgumentException("GPS accuracy " + accuracy + " m exceeds maximum allowed threshold of " + MAX_ALLOWABLE_ACCURACY_METERS + " m.");
            }
        }
    }
}
