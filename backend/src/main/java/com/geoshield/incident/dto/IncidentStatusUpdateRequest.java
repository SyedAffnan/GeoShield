package com.geoshield.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IncidentStatusUpdateRequest(
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "^(ACKNOWLEDGED|RESPONDING|RESOLVED|CANCELLED)$",
                message = "Status must be one of: ACKNOWLEDGED, RESPONDING, RESOLVED, CANCELLED")
        String status
) { }
