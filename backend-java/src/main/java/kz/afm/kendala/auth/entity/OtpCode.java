package kz.afm.kendala.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.auth.enums.OtpPurpose;

@Entity
@Table(name = "otp_codes")
public class OtpCode {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 64)
    private String recipient;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "maximum_attempts", nullable = false)
    private int maximumAttempts = 5;

    @Column(name = "cooldown_until")
    private Instant cooldownUntil;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "provider_status", nullable = false, length = 32)
    private String providerStatus = "PENDING";

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "request_correlation_id", length = 100)
    private String requestCorrelationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (recipient == null || recipient.isBlank()) recipient = phone;
        if (maximumAttempts <= 0) maximumAttempts = 5;
        if (providerStatus == null || providerStatus.isBlank()) providerStatus = "PENDING";
        Instant now = Instant.now();
        createdAt = now;
        lastSentAt = now;
    }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) {
        this.phone = phone;
        if (recipient == null || recipient.isBlank()) {
            recipient = phone;
        }
    }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public OtpPurpose getPurpose() { return purpose; }
    public void setPurpose(OtpPurpose purpose) { this.purpose = purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public int getMaximumAttempts() { return maximumAttempts; }
    public void setMaximumAttempts(int maximumAttempts) { this.maximumAttempts = maximumAttempts; }
    public Instant getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(Instant cooldownUntil) { this.cooldownUntil = cooldownUntil; }
    public Instant getInvalidatedAt() { return invalidatedAt; }
    public void setInvalidatedAt(Instant invalidatedAt) { this.invalidatedAt = invalidatedAt; }
    public String getProviderStatus() { return providerStatus; }
    public void setProviderStatus(String providerStatus) { this.providerStatus = providerStatus; }
    public String getProviderMessageId() { return providerMessageId; }
    public void setProviderMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getRequestCorrelationId() { return requestCorrelationId; }
    public void setRequestCorrelationId(String requestCorrelationId) { this.requestCorrelationId = requestCorrelationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
}
