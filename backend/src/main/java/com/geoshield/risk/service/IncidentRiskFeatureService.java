package com.geoshield.risk.service;

import com.geoshield.incident.dto.IncidentResponse;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Calculates the normalized real-time user-report risk feature from actual stored incidents
 * with full provenance traceability.
 *
 * <p>The feature evaluates active incidents within a defined spatial radius (10 km) and temporal
 * window (24 hours) relative to the tourist's current location, decaying risk linearly with
 * distance and elapsed time. Incident severity is determined from the reported incident type,
 * and status weighting accounts for active emergency response progress.
 *
 * <p>Calculation uses the tourist's precise coordinates for spatial distance lookup, while provenance
 * records the privacy-preserving grid representation.
 */
@Service
public class IncidentRiskFeatureService {
    public static final double MAX_DISTANCE_KM = 10.0;
    public static final double MAX_AGE_HOURS = 24.0;
    public static final String SOURCE = "GeoShield Incident Reports";
    public static final String SOURCE_TYPE = "USER_INCIDENTS";
    public static final String NORMALIZATION =
            "Sum of distance- and recency-decayed active incident severities within 10 km and 24h, clamped to [0, 100]";

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final Clock clock;

    @Autowired
    public IncidentRiskFeatureService() {
        this(Clock.systemUTC());
    }

    public IncidentRiskFeatureService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Calculates the normalized risk score for user-reported incidents near the given coordinates.
     */
    public NormalizedRiskFeature userReportRisk(List<IncidentResponse> incidents, BigDecimal latitude,
            BigDecimal longitude) {
        return userReportRisk(incidents, latitude, longitude, clock.instant());
    }

    /**
     * Calculates the normalized risk score evaluated at a specific instant (useful for deterministic tests).
     */
    public NormalizedRiskFeature userReportRisk(List<IncidentResponse> incidents, BigDecimal latitude,
            BigDecimal longitude, Instant now) {
        String scope = GeographicProvenanceUtil.toRadiusAroundLocationGrid(latitude, longitude, MAX_DISTANCE_KM);
        if (latitude == null || longitude == null) {
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.USER_REPORT,
                    SOURCE,
                    "No current location is available to determine spatial incident relevance.",
                    "Requires tourist coordinates to calculate spatial proximity; no score is synthesized.",
                    "INCIDENT_DATA_UNAVAILABLE",
                    null,
                    SOURCE_TYPE,
                    null,
                    null,
                    null,
                    scope,
                    "Requires tourist coordinates to calculate spatial proximity; no score is synthesized.");
        }

        if (incidents == null || incidents.isEmpty()) {
            String sourceId = String.format(Locale.ROOT,
                    "QUERY(radius=%.1fkm,window=%.1fh,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=0)",
                    MAX_DISTANCE_KM, MAX_AGE_HOURS);
            return new NormalizedRiskFeature(
                    RiskFactorType.USER_REPORT,
                    BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP),
                    true,
                    SOURCE,
                    "No active user-reported incidents found within " + MAX_DISTANCE_KM + " km in the last 24 hours.",
                    NORMALIZATION,
                    null,
                    "0 active incidents",
                    SOURCE_TYPE,
                    sourceId,
                    null,
                    null,
                    scope,
                    NORMALIZATION);
        }

        double accumulatedRisk = 0.0;
        int relevantCount = 0;
        List<IncidentResponse> contributing = new ArrayList<>();

        for (IncidentResponse incident : incidents) {
            if (incident.latitude() == null || incident.longitude() == null) {
                continue;
            }

            double distanceKm = haversineDistanceKm(latitude.doubleValue(), longitude.doubleValue(),
                    incident.latitude().doubleValue(), incident.longitude().doubleValue());
            if (distanceKm > MAX_DISTANCE_KM) {
                continue;
            }

            Instant incidentTime = incident.reportedAt() != null ? incident.reportedAt() : now;
            long secondsBetween = Duration.between(incidentTime, now).getSeconds();
            double ageHours = Math.max(0.0, secondsBetween / 3600.0);
            if (ageHours > MAX_AGE_HOURS) {
                continue;
            }

            double statusWeight = statusWeight(incident.status());
            if (statusWeight <= 0.0) {
                continue;
            }

            double baseSeverity = severityForType(incident.incidentType());
            double spatialWeight = 1.0 - (distanceKm / MAX_DISTANCE_KM);
            double temporalWeight = 1.0 - (ageHours / MAX_AGE_HOURS);

            double incidentScore = baseSeverity * spatialWeight * temporalWeight * statusWeight;
            accumulatedRisk += incidentScore;
            relevantCount++;
            contributing.add(incident);
        }

        double clampedScore = Math.min(100.0, Math.max(0.0, accumulatedRisk));
        BigDecimal normalizedValue = BigDecimal.valueOf(clampedScore).setScale(8, RoundingMode.HALF_UP);

        String reason = relevantCount == 0
                ? "No active user-reported incidents found within " + MAX_DISTANCE_KM + " km in the last 24 hours."
                : "Evaluated " + relevantCount + " active user-reported incident(s) within " + MAX_DISTANCE_KM
                        + " km; aggregated raw severity is " + String.format(Locale.ROOT, "%.2f", accumulatedRisk)
                        + " (clamped to [0, 100]).";

        String sourceId;
        Instant observedAt = null;
        Long freshnessSeconds = null;

        if (relevantCount > 0) {
            // Sort deterministically: newest first, tie-break by incidentId
            contributing.sort((a, b) -> {
                Instant ta = a.reportedAt() != null ? a.reportedAt() : Instant.EPOCH;
                Instant tb = b.reportedAt() != null ? b.reportedAt() : Instant.EPOCH;
                int cmp = tb.compareTo(ta);
                if (cmp != 0) return cmp;
                return a.incidentId().compareTo(b.incidentId());
            });

            observedAt = contributing.get(0).reportedAt();
            if (observedAt != null) {
                freshnessSeconds = Math.max(0L, Duration.between(observedAt, now).getSeconds());
            }

            int limit = Math.min(5, contributing.size());
            StringBuilder idsSb = new StringBuilder();
            for (int i = 0; i < limit; i++) {
                if (i > 0) idsSb.append(",");
                idsSb.append(contributing.get(i).incidentId());
            }
            if (contributing.size() > 5) {
                idsSb.append(",...+").append(contributing.size() - 5).append("_more");
            }

            sourceId = String.format(Locale.ROOT,
                    "QUERY(radius=%.1fkm,window=%.1fh,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=%d,ids=[%s])",
                    MAX_DISTANCE_KM, MAX_AGE_HOURS, relevantCount, idsSb.toString());
        } else {
            sourceId = String.format(Locale.ROOT,
                    "QUERY(radius=%.1fkm,window=%.1fh,status=[REPORTED,ACKNOWLEDGED,RESPONDING],matched=0)",
                    MAX_DISTANCE_KM, MAX_AGE_HOURS);
        }

        String rawValue = relevantCount == 0
                ? "0 active incidents"
                : String.format(Locale.ROOT, "%d active incident(s), raw score %.2f", relevantCount, accumulatedRisk);

        String normDetails = relevantCount == 0
                ? NORMALIZATION
                : String.format(Locale.ROOT, "raw severity %.2f clamped to [0, 100] = %.2f", accumulatedRisk, clampedScore);

        return new NormalizedRiskFeature(
                RiskFactorType.USER_REPORT,
                normalizedValue,
                true,
                SOURCE,
                reason,
                NORMALIZATION,
                null,
                rawValue,
                SOURCE_TYPE,
                sourceId,
                observedAt,
                freshnessSeconds,
                scope,
                normDetails);
    }

    /**
     * Backwards-compatible overload for callers without location context.
     * Always reports unavailable because spatial relevance cannot be established.
     */
    public NormalizedRiskFeature userReportRisk(List<IncidentResponse> incidents) {
        return userReportRisk(incidents, null, null);
    }

    public static double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0) * Math.cos(lat1Rad) * Math.cos(lat2Rad);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    public static double statusWeight(String status) {
        if (status == null) {
            return 0.0;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "REPORTED", "ACKNOWLEDGED" -> 1.0;
            case "RESPONDING" -> 0.8;
            case "RESOLVED", "CANCELLED" -> 0.0;
            default -> 0.0;
        };
    }

    public static double severityForType(String incidentType) {
        if (incidentType == null || incidentType.isBlank()) {
            return 50.0;
        }
        String upper = incidentType.toUpperCase(Locale.ROOT);
        if (upper.contains("ASSAULT") || upper.contains("ATTACK") || upper.contains("ROBBERY")
                || upper.contains("WEAPON") || upper.contains("VIOLEN") || upper.contains("TERROR")
                || upper.contains("KIDNAP") || upper.contains("HOMICIDE") || upper.contains("SHOOTING")) {
            return 100.0;
        }
        if (upper.contains("THEFT") || upper.contains("STOLEN") || upper.contains("BURGLAR")
                || upper.contains("HARASS") || upper.contains("STALK") || upper.contains("EXTORT")
                || upper.contains("FRAUD") || upper.contains("SCAM") || upper.contains("THREAT")) {
            return 75.0;
        }
        if (upper.contains("ACCIDENT") || upper.contains("COLLISION") || upper.contains("CRASH")
                || upper.contains("FIRE") || upper.contains("MEDICAL") || upper.contains("INJURY")
                || upper.contains("EMERGENCY")) {
            return 50.0;
        }
        if (upper.contains("HAZARD") || upper.contains("TRAFFIC") || upper.contains("OBSTRUCT")
                || upper.contains("DEBRIS") || upper.contains("DISTURB") || upper.contains("SUSPICIOUS")
                || upper.contains("OTHER")) {
            return 25.0;
        }
        return 50.0;
    }
}
