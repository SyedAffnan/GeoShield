package com.geoshield.identity.dto;

import com.geoshield.identity.entity.Role;
import java.util.UUID;

public record RegisterResponse(UUID userId, String username, String email, Role role) { }
