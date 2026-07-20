package com.geoshield.identity.dto;

import com.geoshield.identity.entity.Role;

public record LoginResponse(String accessToken, String refreshToken, long expiresIn, Role role) { }
