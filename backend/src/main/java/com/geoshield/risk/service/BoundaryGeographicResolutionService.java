package com.geoshield.risk.service;

import com.geoshield.historicaldata.entity.GeographicLevel;
import com.geoshield.risk.dto.GeographicResolution;
import com.geoshield.risk.geo.StateBoundaryIndex;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Resolves a tourist GPS coordinate to a State/UT by deterministic point-in-polygon
 * lookup against the bundled Survey of India / NWIC derived boundary resource.
 *
 * <p>The resolved {@code geographicUnit} is spelled exactly as the MoRTH
 * {@code geographicUnit}, because the offline preprocessing step normalized the four
 * differing source names. No fuzzy matching, centroid fallback, bounding-box match,
 * coordinate-range rule, or external geocoding service is used, and the service never
 * guesses: a coordinate that no polygon contains returns an explicit unresolved result.
 */
@Service
public class BoundaryGeographicResolutionService implements GeographicResolutionService {
    private static final BigDecimal MINIMUM_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAXIMUM_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MINIMUM_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAXIMUM_LONGITUDE = new BigDecimal("180");

    private final StateBoundaryIndex stateBoundaryIndex;

    public BoundaryGeographicResolutionService(StateBoundaryIndex stateBoundaryIndex) {
        this.stateBoundaryIndex = stateBoundaryIndex;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns an unresolved result rather than throwing for absent or out-of-range
     * input, so an anomalous stored coordinate can never fail the risk request.
     */
    @Override
    public GeographicResolution resolve(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return GeographicResolution.unresolved(
                    "A latitude and longitude are both required to resolve a State/UT.");
        }
        if (isOutside(latitude, MINIMUM_LATITUDE, MAXIMUM_LATITUDE)) {
            return GeographicResolution.unresolved(
                    "Latitude " + latitude.toPlainString() + " is outside the valid range -90 to 90.");
        }
        if (isOutside(longitude, MINIMUM_LONGITUDE, MAXIMUM_LONGITUDE)) {
            return GeographicResolution.unresolved(
                    "Longitude " + longitude.toPlainString() + " is outside the valid range -180 to 180.");
        }

        // GeoJSON positions are [longitude, latitude]; the Location module supplies
        // latitude and longitude separately, so the axis order is set explicitly here.
        return stateBoundaryIndex.resolveStateName(longitude.doubleValue(), latitude.doubleValue())
                .map(stateName -> GeographicResolution.resolved(GeographicLevel.STATE_UT, stateName))
                .orElseGet(() -> GeographicResolution.unresolved(
                        "No India State/UT boundary contains the coordinate " + latitude.toPlainString()
                                + ", " + longitude.toPlainString() + "."));
    }

    private static boolean isOutside(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        return value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0;
    }
}
