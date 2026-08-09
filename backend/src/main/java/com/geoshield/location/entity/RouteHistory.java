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
@Table(name = "route_history", indexes = @Index(name = "idx_route_history_user_started", columnList = "user_id,started_at"))
public class RouteHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id", nullable = false, updatable = false)
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

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    public RouteHistory() { }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getAccuracy() { return accuracy; }
    public BigDecimal getSpeed() { return speed; }
    public Instant getStartedAt() { return startedAt; }
    public void setUser(User user) { this.user = user; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }
    public void setSpeed(BigDecimal speed) { this.speed = speed; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
