package com.geoshield.identity.dto;

import com.geoshield.common.validation.ValidPassword;
import com.geoshield.common.validation.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]{3,30}$", message = "Username must be 3 to 30 alphanumeric characters") String username,
        @NotBlank @Email String email,
        @ValidPassword String password,
        @NotBlank @Size(max = 255) String fullName,
        @ValidPhoneNumber String phoneNumber) { }
