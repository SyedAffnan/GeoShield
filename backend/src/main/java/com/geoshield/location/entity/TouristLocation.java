package com.geoshield.location.entity;

import com.geoshield.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "tourist_locations", indexes = @Index(name = "idx_tourist_locations_user_recorded", columnList = "user_id,recorded_at"))
public class TouristLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 2)
    private BigDecimal accuracy;

    @Column(precision = 10, scale = 2)
    private BigDecimal speed;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public TouristLocation() { }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAccuracy() { return accuracy; }
    public BigDecimal getSpeed() { return speed; }
    public Instant getRecordedAt() { return recordedAt; }
    public void setUser(User user) { this.user = user; }
    public void update(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracy, BigDecimal speed, Instant recordedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.recordedAt = recordedAt;
    }
}
