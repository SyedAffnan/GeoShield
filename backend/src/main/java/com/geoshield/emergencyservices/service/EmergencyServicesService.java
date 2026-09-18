package com.geoshield.emergencyservices.service;

import com.geoshield.common.service.ModuleService;
import com.geoshield.emergencyservices.dto.EmergencyServiceCenterResponse;
import com.geoshield.emergencyservices.dto.NearestFacilityResult;
import com.geoshield.emergencyservices.entity.CenterType;
import java.util.List;
import java.util.Optional;

/**
 * Service contract for emergency service facility queries across medical, police, and fire domains.
 */
public interface EmergencyServicesService extends ModuleService {

    /**
     * Finds the nearest emergency facility regardless of type.
     */
    Optional<NearestFacilityResult> findNearestFacility(double latitude, double longitude);

    /**
     * Finds the nearest emergency facility of a specific type.
     */
    Optional<NearestFacilityResult> findNearestFacilityByType(double latitude, double longitude, CenterType centerType);

    /**
     * Retrieves all available emergency service centers.
     */
    List<EmergencyServiceCenterResponse> getAllCenters();

    /**
     * Returns total number of registered emergency service centers.
     */
    long getCenterCount();
}
