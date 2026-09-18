package com.geoshield.risk.service;

import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.service.EmergencyServicesService;
import com.geoshield.risk.dto.NormalizedRiskFeature;
import com.geoshield.risk.dto.RiskFactorType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Calculates the normalized real-time Emergency Service Proximity risk feature from verified physical facilities.
 *
 * <p>The feature calculates the Haversine distance to the nearest emergency facility (hospital,
 * police station, or fire station) and normalizes it linearly up to a 10.0 km horizon:
 * <ul>
 *   <li>0.0 km &rarr; 0.0 (maximum safety / immediate access)</li>
 *   <li>5.0 km &rarr; 50.0 (moderate distance)</li>
 *   <li>&ge; 10.0 km &rarr; 100.0 (isolated / maximum proximity risk)</li>
 * </ul>
 */
@Service
public class EmergencyServiceProximityRiskService {

    public static final double MAX_PROXIMITY_KM = 10.0;
    public static final String SOURCE = "OpenStreetMap Emergency Amenities (ODbL)";
    public static final String NORMALIZATION =
            "Nearest emergency service facility distance / 10.0 km × 100.0, clamped to [0, 100]";

    private final EmergencyServicesService emergencyServicesService;

    public EmergencyServiceProximityRiskService(EmergencyServicesService emergencyServicesService) {
        this.emergencyServicesService = emergencyServicesService;
    }

    /**
     * Computes the normalized proximity risk for the given tourist coordinates.
     */
    public NormalizedRiskFeature proximityRisk(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.SERVICE_PROXIMITY,
                    SOURCE,
                    "No current location is available to determine emergency service proximity.",
                    "Requires tourist coordinates to calculate proximity; no score is synthesized.");
        }

        Optional<NearestFacilityResult> nearestOpt =
                emergencyServicesService.findNearestFacility(latitude.doubleValue(), longitude.doubleValue());

        if (nearestOpt.isEmpty()) {
            return NormalizedRiskFeature.unavailable(
                    RiskFactorType.SERVICE_PROXIMITY,
                    SOURCE,
                    "No emergency service centers available in database.",
                    "Requires emergency service reference dataset; no score is synthesized.");
        }

        NearestFacilityResult nearest = nearestOpt.get();
        double dMin = nearest.distanceKm();
        double rawRisk = (dMin / MAX_PROXIMITY_KM) * 100.0;
        double clampedScore = Math.min(100.0, Math.max(0.0, rawRisk));
        BigDecimal normalizedValue = BigDecimal.valueOf(clampedScore).setScale(8, RoundingMode.HALF_UP);

        String reason;
        if (dMin >= MAX_PROXIMITY_KM) {
            reason = String.format(Locale.ROOT,
                    "Nearest emergency facility: %s (%s) at %.2f km (exceeds %.1f km threshold; maximum risk applied).",
                    nearest.facility().name(), nearest.facility().centerType(), dMin, MAX_PROXIMITY_KM);
        } else {
            reason = String.format(Locale.ROOT,
                    "Nearest emergency facility: %s (%s) at %.2f km (proximity risk: %.2f).",
                    nearest.facility().name(), nearest.facility().centerType(), dMin, clampedScore);
        }

        return new NormalizedRiskFeature(
                RiskFactorType.SERVICE_PROXIMITY,
                normalizedValue,
                true,
                SOURCE,
                reason,
                NORMALIZATION);
    }
}
