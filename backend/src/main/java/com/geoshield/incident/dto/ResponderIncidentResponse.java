package com.geoshield.incident.dto;

import com.geoshield.incident.entity.IncidentSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResponderIncidentResponse(
        UUID incidentId,
        UUID reporterId,
        String reporterUsername,
        String reporterFullName,
        String reporterPhoneNumber,
        String incidentType,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        IncidentSourceType sourceType,
        Instant createdAt
) { }
