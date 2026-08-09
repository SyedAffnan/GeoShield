package com.geoshield.location.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsLatitudeOutsideApprovedRange() {
        LocationRequest request = new LocationRequest(new BigDecimal("90.1"), BigDecimal.ZERO, null, null, Instant.now());
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsLongitudeOutsideApprovedRange() {
        LocationRequest request = new LocationRequest(BigDecimal.ZERO, new BigDecimal("180.1"), null, null, Instant.now());
        assertThat(validator.validate(request)).isNotEmpty();
    }
}
