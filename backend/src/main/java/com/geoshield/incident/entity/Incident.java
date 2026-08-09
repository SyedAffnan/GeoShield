package com.geoshield.incident.entity;

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
@Table(name = "incidents", indexes = @Index(name = "idx_incidents_status_created", columnList = "status,created_at"))
public class Incident extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "incident_id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "incident_type", nullable = false, length = 100)
    private String incidentType;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "integrity_hash", nullable = false, length = 64, updatable = false)
    private String integrityHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private IncidentSourceType sourceType;

    @Column(name = "client_request_id", nullable = false, unique = true, updatable = false)
    private UUID clientRequestId;

    public Incident() { }

    public void initialize(User reporter, IncidentSourceType sourceType, String status, String integrityHash,
            UUID clientRequestId) {
        this.reporter = reporter;
        this.sourceType = sourceType;
        this.status = status;
        this.integrityHash = integrityHash;
        this.clientRequestId = clientRequestId;
    }

    public UUID getId() { return id; }
    public User getReporter() { return reporter; }
    public String getIncidentType() { return incidentType; }
    public String getDescription() { return description; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getIntegrityHash() { return integrityHash; }
    public IncidentSourceType getSourceType() { return sourceType; }
    public UUID getClientRequestId() { return clientRequestId; }

    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public void setDescription(String description) { this.description = description; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
}
