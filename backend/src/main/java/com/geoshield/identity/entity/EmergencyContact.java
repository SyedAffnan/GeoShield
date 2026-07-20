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
@Table(name = "emergency_contacts", indexes = @Index(name = "idx_emergency_contacts_user_primary", columnList = "user_id,is_primary"))
public class EmergencyContact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contact_id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "contact_name", nullable = false, length = 255)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 16)
    private String contactPhone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    public EmergencyContact() { }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public boolean isPrimary() { return primary; }
    public void setUser(User user) { this.user = user; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public void update(String contactName, String contactPhone, boolean primary) {
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.primary = primary;
    }
}
