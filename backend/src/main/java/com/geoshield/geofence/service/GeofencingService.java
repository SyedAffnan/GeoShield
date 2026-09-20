package com.geoshield.geofence.service;

import com.geoshield.geofence.model.GeofenceEvaluationResult;
import com.geoshield.geofence.model.GeofenceZone;
import com.geoshield.geofence.model.HazardGeometry;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Stateless canonical evaluator for geofence zone transitions.
 *
 * <p>Requirements:
 * <ul>
 *   <li>Completely stateless across requests.</li>
 *   <li>Evaluates supplied GPS coordinates against hazard geometry.</li>
 *   <li>Enforces state-dependent hysteresis on exit boundaries only.</li>
 *   <li>Maintains zero persistent database state.</li>
 *   <li>Owns zero notification cooldown state.</li>
 * </ul>
 */
public interface GeofencingService {

    /**
     * Evaluates a single hazard deterministically against user location and previous zone.
     *
     * @param userLatitude latitude in [-90.0, 90.0]
     * @param userLongitude longitude in [-180.0, 180.0]
     * @param reportedAccuracyMeters reported horizontal GPS accuracy in meters [0.0, 100.0] (nullable)
     * @param previousZone caller-supplied previous zone state
     * @param hazard hazard geometry to evaluate
     * @param evaluatedAt timestamp of location fix
     * @return deterministic evaluation result
     */
    GeofenceEvaluationResult evaluate(
            double userLatitude,
            double userLongitude,
            Double reportedAccuracyMeters,
            GeofenceZone previousZone,
            HazardGeometry hazard,
            Instant evaluatedAt
    );

    /**
     * Evaluates multiple hazards independently in deterministic hazard ID order within a single request.
     *
     * @param userLatitude latitude in [-90.0, 90.0]
     * @param userLongitude longitude in [-180.0, 180.0]
     * @param reportedAccuracyMeters reported horizontal GPS accuracy in meters (nullable)
     * @param previousZones map of hazardId to previous zone state (defaults to OUTSIDE if missing)
     * @param hazards list of hazards to evaluate
     * @param evaluatedAt timestamp of location fix
     * @return deterministic list of evaluation results ordered by hazardId
     */
    List<GeofenceEvaluationResult> evaluateAll(
            double userLatitude,
            double userLongitude,
            Double reportedAccuracyMeters,
            Map<String, GeofenceZone> previousZones,
            List<HazardGeometry> hazards,
            Instant evaluatedAt
    );
}
