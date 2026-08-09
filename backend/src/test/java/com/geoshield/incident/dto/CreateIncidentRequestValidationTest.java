package com.geoshield.incident.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateIncidentRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingRequiredIncidentFields() {
        CreateIncidentRequest request = new CreateIncidentRequest("", "", null, null, null);

        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void rejectsCoordinatesOutsideApprovedRanges() {
        CreateIncidentRequest request = new CreateIncidentRequest("Road hazard", "Debris on road",
                new BigDecimal("90.0001"), new BigDecimal("180.0001"), UUID.randomUUID());

        assertFalse(validator.validate(request).isEmpty());
    }
}
