package com.geoshield.identity.entity;

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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "device_tokens", indexes = @Index(name = "idx_device_tokens_user_active", columnList = "user_id,is_active"))
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "fcm_token", nullable = false, unique = true, length = 512)
    private String fcmToken;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected DeviceToken() { }

    public DeviceToken(User user, String fcmToken) {
        this.user = user;
        this.fcmToken = fcmToken;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getFcmToken() { return fcmToken; }
    public boolean isActive() { return active; }
    public void activate() { active = true; }
    public void deactivate() { active = false; }
}
