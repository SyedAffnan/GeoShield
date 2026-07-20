package com.geoshield.identity.dto;

import com.geoshield.identity.entity.Role;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileResponse(UUID userId, String username, String email, String fullName, String phoneNumber,
        LocalDate dateOfBirth, String gender, String nationality, String address, String profileImageUrl, Role role,
        boolean active) { }
