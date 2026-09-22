package com.geoshield.sos.dto;

import com.geoshield.sos.entity.SosStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SosResponse(
        UUID sosId,
        UUID userId,
        String username,
        String fullName,
        String phoneNumber,
        BigDecimal latitude,
        BigDecimal longitude,
        SosStatus status,
        UUID assignedResponderId,
        UUID clientRequestId,
        Instant triggeredAt,
        Instant acknowledgedAt,
        Instant respondingAt,
        Instant resolvedAt,
        Instant cancelledAt
) {
    public SosResponse(
            UUID sosId,
            UUID userId,
            String username,
            String fullName,
            String phoneNumber,
            BigDecimal latitude,
            BigDecimal longitude,
            SosStatus status,
            UUID assignedResponderId,
            UUID clientRequestId,
            Instant triggeredAt
    ) {
        this(sosId, userId, username, fullName, phoneNumber, latitude, longitude, status, assignedResponderId, clientRequestId, triggeredAt, null, null, null, null);
    }
}
