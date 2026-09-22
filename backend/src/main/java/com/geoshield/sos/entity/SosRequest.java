package com.geoshield.sos.entity;

import com.geoshield.common.entity.BaseEntity;
import com.geoshield.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sos_requests", indexes = {
        @Index(name = "idx_sos_status_created", columnList = "status,created_at"),
        @Index(name = "idx_sos_user_created", columnList = "user_id,created_at")
})
public class SosRequest extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sos_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SosStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_responder_id")
    private User assignedResponder;

    @Column(name = "client_request_id", nullable = false, unique = true, updatable = false)
    private UUID clientRequestId;

    @Column(name = "acknowledged_at")
    private java.time.Instant acknowledgedAt;

    @Column(name = "responding_at")
    private java.time.Instant respondingAt;

    @Column(name = "resolved_at")
    private java.time.Instant resolvedAt;

    @Column(name = "cancelled_at")
    private java.time.Instant cancelledAt;

    public SosRequest() { }

    public SosRequest(User user, BigDecimal latitude, BigDecimal longitude, SosStatus status, UUID clientRequestId) {
        this.user = user;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.clientRequestId = clientRequestId;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public SosStatus getStatus() { return status; }
    public User getAssignedResponder() { return assignedResponder; }
    public UUID getClientRequestId() { return clientRequestId; }
    public java.time.Instant getAcknowledgedAt() { return acknowledgedAt; }
    public java.time.Instant getRespondingAt() { return respondingAt; }
    public java.time.Instant getResolvedAt() { return resolvedAt; }
    public java.time.Instant getCancelledAt() { return cancelledAt; }

    public void setStatus(SosStatus status) { this.status = status; }
    public void setAssignedResponder(User assignedResponder) { this.assignedResponder = assignedResponder; }

    public void setAcknowledgedAt(java.time.Instant acknowledgedAt) {
        if (this.acknowledgedAt == null) {
            this.acknowledgedAt = acknowledgedAt;
        }
    }

    public void setRespondingAt(java.time.Instant respondingAt) {
        if (this.respondingAt == null) {
            this.respondingAt = respondingAt;
        }
    }

    public void setResolvedAt(java.time.Instant resolvedAt) {
        if (this.resolvedAt == null) {
            this.resolvedAt = resolvedAt;
        }
    }

    public void setCancelledAt(java.time.Instant cancelledAt) {
        if (this.cancelledAt == null) {
            this.cancelledAt = cancelledAt;
        }
    }
}
