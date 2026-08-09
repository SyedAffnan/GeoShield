package com.geoshield.incident.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentIntegrityHasherTest {
    private final IncidentIntegrityHasher hasher = new IncidentIntegrityHasher();

    @Test
    void hashesEquivalentCoordinatesIndependentlyOfDatabaseScale() {
        UUID reporterId = UUID.randomUUID();
        UUID clientRequestId = UUID.randomUUID();

        String submittedHash = hasher.hash(reporterId, "Road hazard", "Debris on road", new BigDecimal("12.9716"),
                new BigDecimal("77.5946"), clientRequestId);
        String persistedHash = hasher.hash(reporterId, "Road hazard", "Debris on road", new BigDecimal("12.9716000"),
                new BigDecimal("77.5946000"), clientRequestId);

        assertEquals(submittedHash, persistedHash);
    }
}
