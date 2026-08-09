package com.geoshield.incident.dto;

import com.geoshield.incident.entity.IncidentSourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID incidentId,
        String incidentType,
        String description,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        String integrityHash,
        IncidentSourceType sourceType,
        Instant reportedAt) { }
