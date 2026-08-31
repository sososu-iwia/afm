package kz.afm.kendala.application.dto;

import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.enums.DocumentType;

public record DocumentResponse(
        UUID id,
        UUID applicationId,
        DocumentType documentType,
        String originalFileName,
        String contentType,
        long size,
        Instant createdAt,
        String checksum,
        UUID uploadedBy,
        Instant uploadedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(
                d.getId(),
                d.getApplication().getId(),
                d.getDocumentType(),
                d.getOriginalFileName(),
                d.getContentType(),
                d.getSize(),
                d.getCreatedAt(),
                d.getChecksum(),
                d.getUploadedBy() == null ? null : d.getUploadedBy().getId(),
                d.getUploadedAt() == null ? d.getCreatedAt() : d.getUploadedAt()
        );
    }
}
