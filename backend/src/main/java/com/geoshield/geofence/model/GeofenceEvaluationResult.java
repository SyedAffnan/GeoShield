package com.geoshield.geofence.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable evaluation result for a single hazard evaluation.
 *
 * <p>The backend evaluation is strictly stateless. It does not track or report
 * notification cooldowns, which are owned exclusively by the mobile notification layer.</p>
 */
public final class GeofenceEvaluationResult {

    private final String hazardId;
    private final GeofenceZone previousZone;
    private final GeofenceZone nextZone;
    private final double distanceMeters;
    private final boolean transitionOccurred;
    private final Instant evaluatedAt;

    public GeofenceEvaluationResult(String hazardId,
                                    GeofenceZone previousZone,
                                    GeofenceZone nextZone,
                                    double distanceMeters,
                                    boolean transitionOccurred,
                                    Instant evaluatedAt) {
        this.hazardId = Objects.requireNonNull(hazardId, "hazardId must not be null");
        this.previousZone = Objects.requireNonNull(previousZone, "previousZone must not be null");
        this.nextZone = Objects.requireNonNull(nextZone, "nextZone must not be null");
        this.distanceMeters = distanceMeters;
        this.transitionOccurred = transitionOccurred;
        this.evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }

    public String getHazardId() {
        return hazardId;
    }

    public GeofenceZone getPreviousZone() {
        return previousZone;
    }

    public GeofenceZone getNextZone() {
        return nextZone;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public boolean isTransitionOccurred() {
        return transitionOccurred;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeofenceEvaluationResult that = (GeofenceEvaluationResult) o;
        return Double.compare(that.distanceMeters, distanceMeters) == 0 &&
                transitionOccurred == that.transitionOccurred &&
                hazardId.equals(that.hazardId) &&
                previousZone == that.previousZone &&
                nextZone == that.nextZone &&
                evaluatedAt.equals(that.evaluatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hazardId, previousZone, nextZone, distanceMeters, transitionOccurred, evaluatedAt);
    }

    @Override
    public String toString() {
        return "GeofenceEvaluationResult{" +
                "hazardId='" + hazardId + '\'' +
                ", previousZone=" + previousZone +
                ", nextZone=" + nextZone +
                ", distanceMeters=" + distanceMeters +
                ", transitionOccurred=" + transitionOccurred +
                ", evaluatedAt=" + evaluatedAt +
                '}';
    }
}
