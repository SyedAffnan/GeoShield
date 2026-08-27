package com.geoshield.identity.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusUpdateRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) { }
