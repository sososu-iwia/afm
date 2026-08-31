package kz.afm.kendala.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String phone;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    private UserAccountStatus accountStatus = UserAccountStatus.ACTIVE;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(length = 320)
    private String email;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        syncLegacyActiveFlag();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        syncLegacyActiveFlag();
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return getAccountStatus() == UserAccountStatus.ACTIVE && verifiedAt != null;
    }

    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            this.accountStatus = UserAccountStatus.ACTIVE;
            if (this.verifiedAt == null) {
                this.verifiedAt = Instant.now();
            }
        } else if (this.accountStatus == null || this.accountStatus == UserAccountStatus.ACTIVE) {
            this.accountStatus = UserAccountStatus.PENDING_VERIFICATION;
        }
    }

    public UserAccountStatus getAccountStatus() {
        if (accountStatus != null) {
            return accountStatus;
        }
        return active && verifiedAt != null
                ? UserAccountStatus.ACTIVE
                : UserAccountStatus.PENDING_VERIFICATION;
    }

    public void setAccountStatus(UserAccountStatus accountStatus) {
        this.accountStatus = accountStatus;
        syncLegacyActiveFlag();
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    private void syncLegacyActiveFlag() {
        UserAccountStatus status = getAccountStatus();
        this.active = status == UserAccountStatus.ACTIVE;
        if (status == UserAccountStatus.PENDING_VERIFICATION) {
            this.verifiedAt = null;
        } else if (status == UserAccountStatus.ACTIVE && this.verifiedAt == null) {
            this.verifiedAt = Instant.now();
        }
    }
}
