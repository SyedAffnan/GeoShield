package com.geoshield.identity.dto;

import com.geoshield.common.validation.ValidPhoneNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.hibernate.validator.constraints.URL;

public record UpdateProfileRequest(@NotBlank @Size(max = 255) String fullName, @ValidPhoneNumber String phoneNumber,
        @Past LocalDate dateOfBirth, @Size(max = 50) String gender, @Size(max = 100) String nationality,
        @Size(max = 500) String address, @URL @Size(max = 2048) String profileImageUrl) { }
