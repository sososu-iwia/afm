package kz.afm.kendala.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String actor;
    @Column(name = "actor_role", nullable = false, length = 64)
    private String actorRole;
    @Column(nullable = false, length = 128)
    private String action;
    @Column(nullable = false, length = 32)
    private String result;
    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;
    @Column(name = "entity_id", nullable = false, length = 128)
    private String entityId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_value", nullable = false, columnDefinition = "jsonb")
    private String previousValue;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", nullable = false, columnDefinition = "jsonb")
    private String newValue;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String metadata;
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "failure_code", length = 64)
    private String failureCode;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(nullable = false, length = 64)
    private String ip;
    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (result == null || result.isBlank()) result = "SUCCESS";
        if (source == null || source.isBlank()) source = "SYSTEM";
        if (metadata == null || metadata.isBlank()) metadata = "{}";
        occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
