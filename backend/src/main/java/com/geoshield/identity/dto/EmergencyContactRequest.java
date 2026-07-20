package com.geoshield.identity.dto;

import com.geoshield.common.validation.ValidPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmergencyContactRequest(@NotBlank @Size(max = 255) String contactName, @ValidPhoneNumber String contactPhone,
        boolean isPrimary) { }
