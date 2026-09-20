package com.geoshield.geofence.provider;

import com.geoshield.geofence.model.HazardGeometry;

import java.util.Collections;
import java.util.List;

/**
 * Provider interface supplying active hazard geometries for geofence evaluation.
 *
 * <p>In accordance with Phase 0.4 and National+State/UT Black-Spot audits,
 * the production provider returns an empty list because no verified open coordinate-bearing
 * black-spot dataset is available for production ingestion.</p>
 */
@FunctionalInterface
public interface HazardGeometryProvider {

    /**
     * Returns the list of currently active hazard geometries.
     *
     * @return non-null list of active hazards
     */
    List<HazardGeometry> getActiveHazards();

    /**
     * Default production provider returning an empty list.
     */
    static HazardGeometryProvider emptyProduction() {
        return Collections::emptyList;
    }
}
