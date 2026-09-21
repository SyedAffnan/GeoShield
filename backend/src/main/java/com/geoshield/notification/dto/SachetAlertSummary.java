package com.geoshield.notification.dto;

import com.geoshield.notification.entity.SachetAlert;
import java.time.Instant;
import java.util.UUID;

/**
 * Safe client-facing summary record of a SACHET CAP 1.2 disaster alert.
 */
public record SachetAlertSummary(
        UUID id,
        String identifier,
        String sender,
        Instant sentAt,
        String category,
        String event,
        String urgency,
        String severity,
        String certainty,
        Instant effectiveAt,
        Instant expiresAt,
        String headline,
        String description,
        String instruction,
        String areaDesc,
        boolean isSynthetic,
        String status,
        String msgType) {

    public SachetAlertSummary(
            UUID id,
            String identifier,
            String sender,
            Instant sentAt,
            String category,
            String event,
            String urgency,
            String severity,
            String certainty,
            Instant effectiveAt,
            Instant expiresAt,
            String headline,
            String description,
            String instruction,
            String areaDesc,
            boolean isSynthetic) {
        this(id, identifier, sender, sentAt, category, event, urgency, severity, certainty,
                effectiveAt, expiresAt, headline, description, instruction, areaDesc, isSynthetic, null, null);
    }

    public static SachetAlertSummary fromEntity(SachetAlert entity) {
        return new SachetAlertSummary(
                entity.getId(),
                entity.getIdentifier(),
                entity.getSender(),
                entity.getSentAt(),
                entity.getCategory(),
                entity.getEvent(),
                entity.getUrgency(),
                entity.getSeverity(),
                entity.getCertainty(),
                entity.getEffectiveAt(),
                entity.getExpiresAt(),
                entity.getHeadline(),
                entity.getDescription(),
                entity.getInstruction(),
                entity.getAreaDesc(),
                entity.isSynthetic(),
                entity.getStatus(),
                entity.getMsgType()
        );
    }
}
