package kz.afm.kendala.application.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;

@Entity
@Table(name = "additional_document_requests")
public class AdditionalDocumentRequest {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "source_submission_revision", nullable = false)
    private int sourceSubmissionRevision;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_role", nullable = false, length = 32)
    private UserRole requestedByRole;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "additional_document_request_types",
            joinColumns = @JoinColumn(name = "request_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 64)
    private Set<DocumentType> requestedDocumentTypes = new LinkedHashSet<>();

    @Column(nullable = false, length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdditionalDocumentRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "fulfilled_by_submission_revision")
    private Integer fulfilledBySubmissionRevision;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = AdditionalDocumentRequestStatus.OPEN;
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }
    public int getSourceSubmissionRevision() { return sourceSubmissionRevision; }
    public void setSourceSubmissionRevision(int sourceSubmissionRevision) {
        this.sourceSubmissionRevision = sourceSubmissionRevision;
    }
    public User getRequestedBy() { return requestedBy; }
    public void setRequestedBy(User requestedBy) { this.requestedBy = requestedBy; }
    public UserRole getRequestedByRole() { return requestedByRole; }
    public void setRequestedByRole(UserRole requestedByRole) { this.requestedByRole = requestedByRole; }
    public Set<DocumentType> getRequestedDocumentTypes() { return requestedDocumentTypes; }
    public void setRequestedDocumentTypes(Set<DocumentType> requestedDocumentTypes) {
        this.requestedDocumentTypes = requestedDocumentTypes == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(requestedDocumentTypes);
    }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public AdditionalDocumentRequestStatus getStatus() { return status; }
    public void setStatus(AdditionalDocumentRequestStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public void setFulfilledAt(Instant fulfilledAt) { this.fulfilledAt = fulfilledAt; }
    public Integer getFulfilledBySubmissionRevision() { return fulfilledBySubmissionRevision; }
    public void setFulfilledBySubmissionRevision(Integer fulfilledBySubmissionRevision) {
        this.fulfilledBySubmissionRevision = fulfilledBySubmissionRevision;
    }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public long getVersion() { return version; }
}
