package com.geoshield.emergencyservices.dto;

import com.geoshield.emergencyservices.entity.CenterType;
import com.geoshield.emergencyservices.entity.EmergencyServiceCenter;
import java.math.BigDecimal;

public record EmergencyServiceCenterResponse(
        Long id,
        String sourceId,
        String name,
        CenterType centerType,
        BigDecimal latitude,
        BigDecimal longitude,
        String city,
        String state,
        String phoneNumber) {

    public static EmergencyServiceCenterResponse fromEntity(EmergencyServiceCenter center) {
        return new EmergencyServiceCenterResponse(
                center.getId(),
                center.getSourceId(),
                center.getName(),
                center.getCenterType(),
                center.getLatitude(),
                center.getLongitude(),
                center.getCity(),
                center.getState(),
                center.getPhoneNumber());
    }
}
