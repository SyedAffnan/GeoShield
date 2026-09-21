package com.geoshield.notification.entity;

import com.geoshield.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted NDMA SACHET CAP 1.2 disaster alert entity.
 * Represents an authoritative civil defense disaster alert broadcast.
 */
@Entity
@Table(
        name = "sachet_alerts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sachet_identifier_sender", columnNames = {"identifier", "sender"})
        },
        indexes = {
                @Index(name = "idx_sachet_expires_cancelled", columnList = "expires_at,cancelled"),
                @Index(name = "idx_sachet_references", columnList = "references_identifier")
        }
)
public class SachetAlert extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String identifier;

    @Column(nullable = false, length = 150)
    private String sender;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "msg_type", nullable = false, length = 30)
    private String msgType;

    @Column(name = "references_identifier", length = 100)
    private String referencesIdentifier;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 150)
    private String event;

    @Column(nullable = false, length = 30)
    private String urgency;

    @Column(nullable = false, length = 30)
    private String severity;

    @Column(nullable = false, length = 30)
    private String certainty;

    @Column(name = "effective_at", nullable = false)
    private Instant effectiveAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(length = 500)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "area_desc", length = 500)
    private String areaDesc;

    @Column(name = "geometry_type", nullable = false, length = 30)
    private String geometryType;

    @Column(name = "geometry_data", nullable = false, columnDefinition = "TEXT")
    private String geometryData;

    @Column(name = "is_synthetic", nullable = false)
    private boolean isSynthetic;

    @Column(nullable = false)
    private boolean cancelled;

    public SachetAlert() { }

    public SachetAlert(
            String identifier,
            String sender,
            Instant sentAt,
            String status,
            String msgType,
            String referencesIdentifier,
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
            String geometryType,
            String geometryData,
            boolean isSynthetic,
            boolean cancelled) {
        this.identifier = identifier;
        this.sender = sender;
        this.sentAt = sentAt;
        this.status = status;
        this.msgType = msgType;
        this.referencesIdentifier = referencesIdentifier;
        this.category = category;
        this.event = event;
        this.urgency = urgency;
        this.severity = severity;
        this.certainty = certainty;
        this.effectiveAt = effectiveAt;
        this.expiresAt = expiresAt;
        this.headline = headline;
        this.description = description;
        this.instruction = instruction;
        this.areaDesc = areaDesc;
        this.geometryType = geometryType;
        this.geometryData = geometryData;
        this.isSynthetic = isSynthetic;
        this.cancelled = cancelled;
    }

    public UUID getId() { return id; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }
    public String getReferencesIdentifier() { return referencesIdentifier; }
    public void setReferencesIdentifier(String referencesIdentifier) { this.referencesIdentifier = referencesIdentifier; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public String getUrgency() { return urgency; }
    public void setUrgency(String urgency) { this.urgency = urgency; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getCertainty() { return certainty; }
    public void setCertainty(String certainty) { this.certainty = certainty; }
    public Instant getEffectiveAt() { return effectiveAt; }
    public void setEffectiveAt(Instant effectiveAt) { this.effectiveAt = effectiveAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getAreaDesc() { return areaDesc; }
    public void setAreaDesc(String areaDesc) { this.areaDesc = areaDesc; }
    public String getGeometryType() { return geometryType; }
    public void setGeometryType(String geometryType) { this.geometryType = geometryType; }
    public String getGeometryData() { return geometryData; }
    public void setGeometryData(String geometryData) { this.geometryData = geometryData; }
    public boolean isSynthetic() { return isSynthetic; }
    public void setSynthetic(boolean synthetic) { isSynthetic = synthetic; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
