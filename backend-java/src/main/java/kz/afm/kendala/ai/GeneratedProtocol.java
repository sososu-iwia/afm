package kz.afm.kendala.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;

@Entity
@Table(name = "generated_protocols")
public class GeneratedProtocol {
    @Id
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false)
    private User generatedBy;
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;
    @Column(nullable = false)
    private long size;
    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;
    @Column(length = 64)
    private String checksum;
    @Column(name = "template_version", nullable = false, length = 32)
    private String templateVersion;
    /** Язык документа: ru | kz | en. */
    @Column(name = "language", nullable = false, length = 8)
    private String language = "ru";
    @Column(name = "finalized_at", nullable = false)
    private Instant finalizedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finalized_by", nullable = false)
    private User finalizedBy;
    @Column(name = "protocol_number", nullable = false, length = 128)
    private String protocolNumber;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (generatedAt == null) generatedAt = now;
        if (finalizedAt == null) finalizedAt = generatedAt;
        if (finalizedBy == null) finalizedBy = generatedBy;
        if (templateVersion == null || templateVersion.isBlank()) templateVersion = "v1";
        if (protocolNumber == null || protocolNumber.isBlank()) {
            protocolNumber = id.toString();
        }
        createdAt = now;
    }

    public UUID getId() { return id; }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }
    public User getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(User generatedBy) { this.generatedBy = generatedBy; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }
    public User getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(User finalizedBy) { this.finalizedBy = finalizedBy; }
    public String getProtocolNumber() { return protocolNumber; }
    public void setProtocolNumber(String protocolNumber) { this.protocolNumber = protocolNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
