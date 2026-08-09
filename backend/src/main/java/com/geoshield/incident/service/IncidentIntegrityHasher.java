package com.geoshield.incident.service;

import com.geoshield.incident.entity.Incident;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class IncidentIntegrityHasher {
    String hash(UUID reporterId, String incidentType, String description, BigDecimal latitude, BigDecimal longitude,
            UUID clientRequestId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, reporterId.toString());
            add(digest, incidentType);
            add(digest, description);
            add(digest, canonicalDecimal(latitude));
            add(digest, canonicalDecimal(longitude));
            add(digest, clientRequestId.toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    boolean matches(Incident incident) {
        String expected = hash(incident.getReporter().getId(), incident.getIncidentType(), incident.getDescription(),
                incident.getLatitude(), incident.getLongitude(), incident.getClientRequestId());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                incident.getIntegrityHash().getBytes(StandardCharsets.US_ASCII));
    }

    private void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
    }

    private String canonicalDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
