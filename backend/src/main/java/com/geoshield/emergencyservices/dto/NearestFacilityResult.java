package com.geoshield.emergencyservices.dto;

/**
 * Encapsulates the nearest facility lookup result with computed Haversine distance in kilometers.
 */
public record NearestFacilityResult(
        EmergencyServiceCenterResponse facility,
        double distanceKm) {
}
