package kz.afm.kendala.application.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.application.dto.AdditionalDocumentRequestResponse;
import kz.afm.kendala.application.entity.AdditionalDocumentRequest;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.AdditionalDocumentRequestRepository;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.ConflictException;
import kz.afm.kendala.common.exception.NotFoundException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdditionalDocumentRequestService {
    private final AdditionalDocumentRequestRepository repository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusPolicy statusPolicy;
    private final AuditService auditService;

    public AdditionalDocumentRequestService(
            AdditionalDocumentRequestRepository repository,
            ApplicationRepository applicationRepository,
            ApplicationStatusPolicy statusPolicy,
            AuditService auditService
    ) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.statusPolicy = statusPolicy;
        this.auditService = auditService;
    }

    @Transactional
    public AdditionalDocumentRequest create(
            Application application,
            User requestedBy,
            String comment,
            List<DocumentType> documentTypes
    ) {
        if (repository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
                application.getId(), AdditionalDocumentRequestStatus.OPEN).isPresent()) {
            throw new ConflictException("Для заявки уже существует открытый запрос документов");
        }
        Set<DocumentType> uniqueTypes = new LinkedHashSet<>();
        if (documentTypes != null) {
            documentTypes.stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(DocumentType::name))
                    .forEach(uniqueTypes::add);
        }
        AdditionalDocumentRequest request = new AdditionalDocumentRequest();
        request.setApplication(application);
        request.setSourceSubmissionRevision(application.getSubmissionRevision());
        request.setRequestedBy(requestedBy);
        request.setRequestedByRole(requestedBy.getRole());
        request.setRequestedDocumentTypes(uniqueTypes);
        request.setComment(comment.strip());
        request.setStatus(AdditionalDocumentRequestStatus.OPEN);
        request.setCorrelationId(MDC.get("correlationId"));
        AdditionalDocumentRequest saved = repository.save(request);
        auditService.recordDomain(requestedBy, "DOCUMENTS_REQUESTED", "APPLICATION",
                application.getId().toString(), null, Map.of(
                        "requestId", saved.getId(),
                        "sourceSubmissionRevision", saved.getSourceSubmissionRevision(),
                        "requestedDocumentTypes", uniqueTypes.stream().map(Enum::name).toList(),
                        "status", saved.getStatus()));
        return saved;
    }

    @Transactional
    public void fulfillOpenRequest(Application application, int fulfilledByRevision) {
        repository.findOpenLockedByApplicationId(application.getId()).ifPresent(request -> {
            request.setStatus(AdditionalDocumentRequestStatus.FULFILLED);
            request.setFulfilledAt(Instant.now());
            request.setFulfilledBySubmissionRevision(fulfilledByRevision);
            auditService.record(
                    "system", "SYSTEM", "DOCUMENT_REQUEST_FULFILLED", "APPLICATION",
                    application.getId().toString(), null,
                    Map.of(
                            "requestId", request.getId(),
                            "sourceSubmissionRevision", request.getSourceSubmissionRevision(),
                            "fulfilledBySubmissionRevision", fulfilledByRevision,
                            "requestedDocumentTypes", request.getRequestedDocumentTypes().stream()
                                    .map(Enum::name).sorted().toList()),
                    "system", request.getCorrelationId());
        });
    }

    @Transactional(readOnly = true)
    public List<AdditionalDocumentRequestResponse> list(UUID applicationId, User viewer) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (viewer.getRole() == UserRole.APPLICANT) {
            if (!application.getApplicant().getId().equals(viewer.getId())) {
                throw new NotFoundException("Заявка не найдена");
            }
        } else {
            statusPolicy.requireCommissionRead(viewer, application);
        }
        return repository.findByApplicationIdOrderByCreatedAtAsc(applicationId).stream()
                .map(AdditionalDocumentRequestResponse::from)
                .toList();
    }
}
