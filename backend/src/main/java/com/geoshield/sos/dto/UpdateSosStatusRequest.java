package com.geoshield.sos.dto;

import com.geoshield.sos.entity.SosStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSosStatusRequest(
        @NotNull(message = "Status is required")
        SosStatus status
) { }
