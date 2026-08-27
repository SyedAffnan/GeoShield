package com.geoshield.identity.dto;

import com.geoshield.identity.entity.Role;
import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
        UUID userId,
        String username,
        String email,
        String fullName,
        String phoneNumber,
        Role role,
        boolean active,
        Instant createdAt
) { }
