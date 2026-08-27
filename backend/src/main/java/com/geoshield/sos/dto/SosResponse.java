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
        Instant triggeredAt
) { }
