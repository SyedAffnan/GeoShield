package com.geoshield.identity.dto;

import com.geoshield.common.validation.ValidPassword;
import com.geoshield.common.validation.ValidPhoneNumber;
import com.geoshield.identity.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProvisionUserRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @ValidPassword
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name cannot exceed 255 characters")
        String fullName,

        @ValidPhoneNumber
        String phoneNumber,

        @NotNull(message = "Role is required")
        Role role
) { }
