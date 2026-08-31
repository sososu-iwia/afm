package kz.afm.kendala.application.dto;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.entity.AdditionalDocumentRequest;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;

public record AdditionalDocumentRequestResponse(
        UUID id,
        UUID applicationId,
        int sourceSubmissionRevision,
        UUID requestedById,
        UserRole requestedByRole,
        List<DocumentType> requestedDocumentTypes,
        String comment,
        AdditionalDocumentRequestStatus status,
        Instant createdAt,
        Instant fulfilledAt,
        Integer fulfilledBySubmissionRevision,
        long version
) {
    public static AdditionalDocumentRequestResponse from(AdditionalDocumentRequest request) {
        return new AdditionalDocumentRequestResponse(
                request.getId(),
                request.getApplication().getId(),
                request.getSourceSubmissionRevision(),
                request.getRequestedBy().getId(),
                request.getRequestedByRole(),
                request.getRequestedDocumentTypes().stream()
                        .sorted(Comparator.comparing(DocumentType::name))
                        .toList(),
                request.getComment(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getFulfilledAt(),
                request.getFulfilledBySubmissionRevision(),
                request.getVersion()
        );
    }
}
