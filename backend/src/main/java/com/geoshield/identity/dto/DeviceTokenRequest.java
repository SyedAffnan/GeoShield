package com.geoshield.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(@NotBlank @Size(max = 512) String fcmToken) { }
