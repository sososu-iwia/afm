package kz.afm.kendala.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.StatusHistoryResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Decision;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DecisionType;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DecisionRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.service.ApplicationMapper;
import kz.afm.kendala.application.service.AdditionalDocumentRequestService;
import kz.afm.kendala.application.service.ApplicationStatusPolicy;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.ai.ApplicationScore;
import kz.afm.kendala.ai.ApplicationScoreRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.commission.dto.CommissionApplicationDetail;
import kz.afm.kendala.commission.dto.CommissionApplicationListItem;
import kz.afm.kendala.commission.dto.DecisionResponse;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.PaginationPolicy;
import kz.afm.kendala.common.exception.ApiException;
import kz.afm.kendala.common.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import kz.afm.kendala.notification.NotificationService;
import kz.afm.kendala.observability.DomainMetrics;

@Service
public class CommissionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_WHITELIST = Set.of(
            "createdAt", "updatedAt", "requestedAmount", "status", "region", "applicationNumber"
    );
    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final DecisionRepository decisionRepository;
    private final ApplicationMapper mapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DomainMetrics metrics;
    private final ApplicationStatusPolicy statusPolicy;
    private final ApplicationScoreRepository scoreRepository;
    private final AdditionalDocumentRequestService documentRequestService;

    public CommissionService(
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            ApplicationStatusHistoryRepository historyRepository,
            DecisionRepository decisionRepository,
            ApplicationMapper mapper,
            NotificationService notificationService,
            AuditService auditService,
            DomainMetrics metrics,
            ApplicationStatusPolicy statusPolicy,
            ApplicationScoreRepository scoreRepository,
            AdditionalDocumentRequestService documentRequestService
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.decisionRepository = decisionRepository;
        this.mapper = mapper;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.statusPolicy = statusPolicy;
        this.scoreRepository = scoreRepository;
        this.documentRequestService = documentRequestService;
    }

    @Transactional(readOnly = true)
    public PageResponse<CommissionApplicationListItem> list(
            int page, int size,
            ApplicationStatus status, String region,
            BigDecimal minAmount, BigDecimal maxAmount,
            String sortBy, String sortDir,
            User viewer
    ) {
        statusPolicy.requireCommissionList(viewer);
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER",
                    "minAmount не может быть больше maxAmount");
        }
        String safeSort = SORT_WHITELIST.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(
                PaginationPolicy.zeroBased(page),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(dir, safeSort).and(Sort.by(Sort.Direction.ASC, "id"))
        );
        Page<Application> result = applicationRepository.findAll(
                CommissionApplicationSpecification.forCommission(status, region, minAmount, maxAmount),
                pageable
        );
        return new PageResponse<>(
                result.getContent().stream().map(CommissionApplicationListItem::from).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CommissionApplicationDetail getDetail(UUID applicationId, User viewer) {
        Application app = getCommissionApp(applicationId, viewer);
        return detail(app);
    }

    private CommissionApplicationDetail detail(Application app) {
        List<DocumentResponse> docs = documentRepository.findByApplicationId(app.getId())
                .stream().map(DocumentResponse::from).toList();
        List<StatusHistoryResponse> history = historyRepository
                .findByApplicationIdOrderByCreatedAtAsc(app.getId())
                .stream().map(mapper::toResponse).toList();
        List<DecisionResponse> decisions = decisionRepository.findByApplicationIdOrderByCreatedAtAsc(app.getId())
                .stream().map(DecisionResponse::from).toList();
        return new CommissionApplicationDetail(
                app.getId(), app.getApplicationNumber(),
                app.getApplicant().getId(), app.getApplicant().getFullName(),
                app.getStatus(), app.getIinOrBin(), app.getRegion(),
                app.getProductionType(), app.getActivityType(), app.getApplicantCategory(),
                app.getLandArea(), app.getRequestedAmount(), app.getApprovedAmount(), app.getDecisionAt(),
                app.getCreatedAt(), app.getUpdatedAt(),
                app.isPublicVisible(), app.getPublishedAt(),
                docs, history, decisions
        );
    }

    @Transactional
    public CommissionApplicationDetail approve(UUID applicationId, User decidedBy, String reason) {
        return approve(applicationId, decidedBy, reason, null);
    }

    @Transactional
    public CommissionApplicationDetail approve(
            UUID applicationId, User decidedBy, String reason, BigDecimal approvedAmount
    ) {
        return applyDecision(
                applicationId,
                decidedBy,
                DecisionType.APPROVED,
                ApplicationStatus.APPROVED,
                reason,
                approvedAmount
        );
    }

    @Transactional
    public CommissionApplicationDetail reject(UUID applicationId, User decidedBy, String reason) {
        requireReason(reason, "Причина отказа обязательна");
        return applyDecision(
                applicationId,
                decidedBy,
                DecisionType.REJECTED,
                ApplicationStatus.REJECTED,
                reason,
                null
        );
    }

    @Transactional
    public CommissionApplicationDetail requestDocuments(UUID applicationId, User decidedBy, String reason) {
        return requestDocuments(applicationId, decidedBy, reason, List.of());
    }

    @Transactional
    public CommissionApplicationDetail requestDocuments(
            UUID applicationId,
            User decidedBy,
            String reason,
            List<DocumentType> documentTypes
    ) {
        requireReason(reason, "Укажите, какие дополнительные документы требуются");
        return applyDecision(applicationId, decidedBy,
                DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED,
                ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED,
                reason,
                null,
                documentTypes
        );
    }

    @Transactional
    public CommissionApplicationDetail publish(UUID applicationId, User actor) {
        statusPolicy.requireFinalDecisionRole(actor);
        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена", "error.application-not-found"));
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Публиковать можно только одобренные заявки");
        }
        if (!app.isPublicVisible()) {
            app.setPublicVisible(true);
            app.setPublishedAt(Instant.now());
            app.setPublishedBy(actor);
            auditService.recordDomain(actor, "PUBLIC_REGISTRY_PUBLISHED", "APPLICATION",
                    app.getId().toString(), java.util.Map.of("public", false),
                    java.util.Map.of("public", true));
        }
        return detail(app);
    }

    @Transactional
    public CommissionApplicationDetail unpublish(UUID applicationId, User actor) {
        statusPolicy.requireFinalDecisionRole(actor);
        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена", "error.application-not-found"));
        if (app.isPublicVisible()) {
            app.setPublicVisible(false);
            app.setPublishedAt(null);
            app.setPublishedBy(null);
            auditService.recordDomain(actor, "PUBLIC_REGISTRY_UNPUBLISHED", "APPLICATION",
                    app.getId().toString(), java.util.Map.of("public", true),
                    java.util.Map.of("public", false));
        }
        return detail(app);
    }

    private CommissionApplicationDetail applyDecision(
            UUID applicationId, User decidedBy,
            DecisionType decisionType, ApplicationStatus newStatus, String reason, BigDecimal approvedAmount
    ) {
        return applyDecision(applicationId, decidedBy, decisionType, newStatus, reason, approvedAmount, List.of());
    }

    private CommissionApplicationDetail applyDecision(
            UUID applicationId, User decidedBy,
            DecisionType decisionType, ApplicationStatus newStatus, String reason, BigDecimal approvedAmount,
            List<DocumentType> requestedDocumentTypes
    ) {
        if (decisionType == DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED) {
            statusPolicy.requireDocumentRequestRole(decidedBy);
        } else {
            statusPolicy.requireFinalDecisionRole(decidedBy);
        }

        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "Заявка не найдена",
                        "error.application-not-found"
                ));
        statusPolicy.requireCommissionRead(decidedBy, app);
        if (decisionType == DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED) {
            statusPolicy.requireDocumentsRequestable(app);
        } else {
            statusPolicy.requireDecidable(app);
        }

        ApplicationStatus previous = app.getStatus();
        app.setStatus(newStatus);
        BigDecimal effectiveApprovedAmount = null;
        if (decisionType == DecisionType.APPROVED) {
            effectiveApprovedAmount = approvedAmount != null ? approvedAmount : app.getRequestedAmount();
            statusPolicy.requireApprovedAmountAllowed(app.getRequestedAmount(), effectiveApprovedAmount);
            app.setApprovedAmount(effectiveApprovedAmount);
            app.setDecisionAt(Instant.now());
        } else if (decisionType == DecisionType.REJECTED) {
            app.setApprovedAmount(null);
            app.setDecisionAt(Instant.now());
        }

        Decision decision = new Decision();
        decision.setApplication(app);
        decision.setDecidedBy(decidedBy);
        decision.setDecisionType(decisionType);
        decision.setReason(reason);
        decision.setActorRole(decidedBy.getRole());
        decision.setComment(reason);
        decision.setRequestedAmount(app.getRequestedAmount());
        decision.setApprovedAmount(effectiveApprovedAmount);
        decision.setCorrelationId(MDC.get("correlationId"));
        decision.setSubmissionRevision(app.getSubmissionRevision());
        if (decisionType == DecisionType.ADDITIONAL_DOCUMENTS_REQUESTED) {
            documentRequestService.create(app, decidedBy, reason, requestedDocumentTypes);
        }
        if (decisionType == DecisionType.APPROVED || decisionType == DecisionType.REJECTED) {
            ApplicationScore score = scoreRepository.findById(app.getId())
                    .filter(candidate -> candidate.getSubmissionRevision() == app.getSubmissionRevision()
                            && candidate.getJob().getStatus() == AiTaskStatus.COMPLETED
                            && candidate.getJob().getSubmissionRevision() != null
                            && candidate.getJob().getSubmissionRevision() == app.getSubmissionRevision())
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.CONFLICT,
                            "AI_PROCESSING_INCOMPLETE",
                            "Финальное решение недоступно без актуального результата скоринга"));
            decision.setScoringJob(score.getJob());
            decision.setAiScore(score.getScore());
            decision.setAiRiskCategory(score.getRiskCategory());
            decision.setAiRecommendedAmount(score.getRecommendedAmount());
            decision.setAiTopFactors(score.getTopFactors());
            decision.setAiLlmSummary(score.getLlmSummary());
            decision.setAiModelName(score.getModelName());
            decision.setAiModelVersion(score.getModelVersion());
        }
        decisionRepository.save(decision);

        kz.afm.kendala.application.entity.ApplicationStatusHistory history =
                new kz.afm.kendala.application.entity.ApplicationStatusHistory();
        history.setApplication(app);
        history.setPreviousStatus(previous);
        history.setNewStatus(newStatus);
        history.setChangedBy(decidedBy);
        history.setReason(decisionType.name());
        history.setComment(reason);
        historyRepository.save(history);
        notificationService.enqueueDecision(app, newStatus, reason);
        var newValue = new LinkedHashMap<String, Object>();
        newValue.put("status", newStatus);
        newValue.put("decisionType", decisionType);
        newValue.put("reason", reason);
        newValue.put("approvedAmount", app.getApprovedAmount());
        auditService.recordDomain(decidedBy, "COMMISSION_DECISION", "APPLICATION",
                app.getId().toString(), java.util.Map.of("status", previous), newValue);
        if (decisionType == DecisionType.APPROVED) {
            metrics.increment("application.approved");
        } else if (decisionType == DecisionType.REJECTED) {
            metrics.increment("application.rejected");
        }

        return getDetail(applicationId, decidedBy);
    }

    public Application getCommissionApp(UUID applicationId, User viewer) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException(
                        "Заявка не найдена",
                        "error.application-not-found"
                ));
        statusPolicy.requireCommissionRead(viewer, app);
        return app;
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        }
        int length = reason.strip().length();
        if (length < 3 || length > 2000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Комментарий должен содержать от 3 до 2000 символов");
        }
    }
}
