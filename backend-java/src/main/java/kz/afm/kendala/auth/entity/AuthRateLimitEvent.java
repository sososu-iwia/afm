package kz.afm.kendala.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_rate_limit_events")
public class AuthRateLimitEvent {

    @Id
    private UUID id;

    @Column(name = "key_type", nullable = false, length = 16)
    private String keyType;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public void setKeyType(String keyType) { this.keyType = keyType; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
}

