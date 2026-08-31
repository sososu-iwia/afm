package kz.afm.kendala.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.ai.dto.AiTaskResponse;
import kz.afm.kendala.ai.dto.ApplicationProcessingResponse;
import kz.afm.kendala.ai.dto.DuplicateProcessingResponse;
import kz.afm.kendala.ai.dto.LlmConclusionProcessingResponse;
import kz.afm.kendala.ai.dto.OcrProcessingResponse;
import kz.afm.kendala.ai.dto.OcrResultResponse;
import kz.afm.kendala.ai.dto.OcrStatusResponse;
import kz.afm.kendala.ai.dto.ScoreResultResponse;
import kz.afm.kendala.ai.dto.ScoreStatusResponse;
import kz.afm.kendala.ai.dto.ScoringProcessingResponse;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiOrchestrationService {
    private static final Set<AiTaskStatus> REUSABLE = EnumSet.of(
            AiTaskStatus.PENDING,
            AiTaskStatus.PROCESSING,
            AiTaskStatus.FAILED_RETRYABLE,
            AiTaskStatus.COMPLETED,
            AiTaskStatus.FAILED_OPTIONAL,
            AiTaskStatus.SKIPPED
    );
    private static final Set<UserRole> COMMISSION_ROLES = EnumSet.of(
            UserRole.COMMISSION_MEMBER, UserRole.CHAIRMAN, UserRole.SECRETARY, UserRole.ADMIN);
    private static final Set<ApplicationStatus> AI_VIEWABLE_STATUSES = EnumSet.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.IN_REVIEW,
            ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED,
            ApplicationStatus.APPROVED, ApplicationStatus.REJECTED);
    private static final Set<ApplicationStatus> AI_TRIGGERABLE_STATUSES = EnumSet.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.IN_REVIEW);

    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final AiProcessingJobRepository jobRepository;
    private final ApplicationScoreRepository scoreRepository;
    private final DocumentOcrResultRepository ocrRepository;
    private final ApplicationSnapshotRepository snapshotRepository;
    private final ApplicationCompletenessService completenessService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final int maxAttempts;

    public AiOrchestrationService(
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            AiProcessingJobRepository jobRepository,
            ApplicationScoreRepository scoreRepository,
            DocumentOcrResultRepository ocrRepository,
            ApplicationSnapshotRepository snapshotRepository,
            ApplicationCompletenessService completenessService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${app.ai.max-attempts:3}") int maxAttempts
    ) {
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.scoreRepository = scoreRepository;
        this.ocrRepository = ocrRepository;
        this.snapshotRepository = snapshotRepository;
        this.completenessService = completenessService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public ScoreStatusResponse triggerScore(UUID applicationId, User user) {
        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        requireApplicationAccess(app, user);
        if (!AI_TRIGGERABLE_STATUSES.contains(app.getStatus())) {
            throw new BusinessRuleException("Скоринг доступен только для отправленной заявки");
        }
        if (!completenessService.check(app).complete()) {
            throw new BusinessRuleException(
                    "Скоринг доступен только после успешного OCR обязательных документов");
        }
        var latest = jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.SCORING, applicationId, app.getSubmissionRevision());
        if (latest.isPresent() && !REUSABLE.contains(latest.get().getStatus())) {
            throw new BusinessRuleException(
                    "Failed scoring можно повторить только через административный retry AI job");
        }
        AiProcessingJob job = latest.orElseGet(() -> queue(AiOperationType.SCORING, app, null, user));
        return scoreStatus(job);
    }

    /**
     * Повторно запрашивает текстовое заключение.
     *
     * Скоринг детерминирован и привязан к ревизии подачи, пересчитывать его незачем.
     * А LLM — внешний сервис: он мог быть не настроен или недоступен в момент подачи,
     * поэтому заключение должно повторяться без новой подачи заявки.
     */
    @Transactional
    public ScoreStatusResponse retryLlmConclusion(UUID applicationId, User user) {
        Application app = applicationRepository.findLockedById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        requireApplicationAccess(app, user);

        AiProcessingJob scoring = jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.SCORING, applicationId, app.getSubmissionRevision())
                .orElseThrow(() -> new BusinessRuleException(
                        "Заключение формируется после скоринга"));
        if (scoring.getStatus() != AiTaskStatus.COMPLETED) {
            throw new BusinessRuleException("Заключение формируется после успешного скоринга");
        }

        var existing = jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.LLM_CONCLUSION, applicationId, app.getSubmissionRevision());
        if (existing.isEmpty()) {
            return scoreStatus(queue(AiOperationType.LLM_CONCLUSION, app, null, user));
        }

        // Ключ идемпотентности уникален для пары «ревизия + операция», поэтому
        // повтор — это сброс существующей задачи, а не создание второй.
        AiProcessingJob job = existing.get();
        if (job.getStatus() == AiTaskStatus.PENDING || job.getStatus() == AiTaskStatus.PROCESSING) {
            return scoreStatus(job);
        }
        job.setStatus(AiTaskStatus.PENDING);
        job.setAttemptCount(0);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setNextAttemptAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        AiProcessingJob saved = jobRepository.save(job);
        eventPublisher.publishEvent(new AiJobQueuedEvent(saved.getId()));
        auditService.recordDomain(user, "LLM_CONCLUSION_RETRIED", "APPLICATION",
                app.getId().toString(), null, Map.of(
                        "jobId", saved.getId(),
                        "submissionRevision", app.getSubmissionRevision()));
        return scoreStatus(saved);
    }

    @Transactional(readOnly = true)
    public ScoreStatusResponse getScore(UUID applicationId, User user) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        requireApplicationAccess(app, user);
        AiProcessingJob job = jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.SCORING, applicationId, app.getSubmissionRevision())
                .orElseThrow(() -> new NotFoundException("Скоринг ещё не запускался"));
        return scoreStatus(job);
    }

    @Transactional
    public OcrStatusResponse triggerOcr(UUID documentId, User user) {
        Document document = documentRepository.findLockedById(documentId)
                .orElseThrow(() -> new NotFoundException("Документ не найден"));
        requireDocumentAccess(document, user);
        if (!AI_TRIGGERABLE_STATUSES.contains(document.getApplication().getStatus())) {
            throw new BusinessRuleException("OCR нельзя повторно запускать для финальной заявки");
        }
        AiProcessingJob job = jobRepository
                .findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(
                        AiOperationType.OCR, documentId)
                .filter(candidate -> REUSABLE.contains(candidate.getStatus()))
                .orElseGet(() -> queue(AiOperationType.OCR, document.getApplication(), document, user));
        return ocrStatus(job);
    }

    @Transactional(readOnly = true)
    public OcrStatusResponse getOcr(UUID documentId, User user) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Документ не найден"));
        requireDocumentAccess(document, user);
        AiProcessingJob job = jobRepository
                .findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(AiOperationType.OCR, documentId)
                .orElseThrow(() -> new NotFoundException("OCR ещё не запускался"));
        return ocrStatus(job);
    }

    @Transactional
    public void startAutomaticWorkflow(Application app, User requestedBy, CompletenessResult completeness) {
        createSnapshot(app, requestedBy, completeness);
        List<Document> documents = documentRepository.findByApplicationId(app.getId());
        documents.forEach(document -> queueIfAbsent(AiOperationType.OCR, app, document, requestedBy));
        queueIfAbsent(AiOperationType.DUPLICATE_CHECK, app, null, requestedBy);
        if (requiredOcrReady(completeness.requiredTypes(), documents)) {
            queueIfAbsent(AiOperationType.SCORING, app, null, requestedBy);
        }
        auditService.recordDomain(requestedBy, "AI_WORKFLOW_STARTED", "APPLICATION",
                app.getId().toString(), null, Map.of(
                        "documents", documents.size(),
                        "completeness", completeness.complete(),
                        "submissionRevision", app.getSubmissionRevision()
                ));
        if (app.getSubmissionRevision() > 1) {
            auditService.recordDomain(requestedBy, "AI_WORKFLOW_SUPERSEDED", "APPLICATION",
                    app.getId().toString(), null, Map.of(
                            "supersededRevision", app.getSubmissionRevision() - 1,
                            "currentRevision", app.getSubmissionRevision()));
        }
    }

    @Transactional(readOnly = true)
    public ApplicationProcessingResponse getProcessing(UUID applicationId, User user) {
        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        requireApplicationAccess(app, user);

        CompletenessResult completeness = completenessService.check(app);
        List<Document> documents = documentRepository.findByApplicationId(app.getId());
        List<AiProcessingJob> jobs = jobRepository.findByApplicationIdOrderByCreatedAtAsc(app.getId())
                .stream()
                .filter(job -> job.getDocument() != null
                        || Integer.valueOf(app.getSubmissionRevision()).equals(job.getSubmissionRevision()))
                .toList();
        OcrProcessingResponse ocr = ocrProcessing(
                documents, jobs, completeness.requiredTypes());
        DuplicateProcessingResponse duplicate = duplicateProcessing(latest(jobs, AiOperationType.DUPLICATE_CHECK));
        ScoringProcessingResponse scoring = scoringProcessing(latest(jobs, AiOperationType.SCORING), app.getId());
        LlmConclusionProcessingResponse llm = llmProcessing(latest(jobs, AiOperationType.LLM_CONCLUSION), app.getId());

        return new ApplicationProcessingResponse(
                app.getId(),
                overallStatus(ocr, duplicate, scoring, llm, jobs),
                completeness,
                ocr,
                duplicate,
                scoring,
                llm,
                app.getSubmissionRevision()
        );
    }

    private AiProcessingJob queue(
            AiOperationType operation, Application app, Document document, User requestedBy
    ) {
        AiProcessingJob job = new AiProcessingJob();
        job.setOperationType(operation);
        job.setApplication(app);
        job.setDocument(document);
        job.setSubmissionRevision(document == null ? app.getSubmissionRevision() : null);
        job.setStatus(AiTaskStatus.PENDING);
        job.setMaxAttempts(maxAttempts);
        job.setCorrelationId(MDC.get("correlationId"));
        job.setIdempotencyKey(document == null
                ? app.getId() + ":" + app.getSubmissionRevision() + ":" + operation
                : document.getId() + ":OCR");
        AiProcessingJob saved = jobRepository.save(job);
        eventPublisher.publishEvent(new AiJobQueuedEvent(saved.getId()));
        String entityType = document == null ? "APPLICATION" : "DOCUMENT";
        String entityId = document == null ? app.getId().toString() : document.getId().toString();
        auditService.recordDomain(requestedBy, operation.name() + "_QUEUED", entityType, entityId,
                null, Map.of(
                        "jobId", saved.getId(),
                        "operation", operation,
                        "submissionRevision", document == null ? app.getSubmissionRevision() : "document-scoped",
                        "status", saved.getStatus(),
                        "maxAttempts", saved.getMaxAttempts()));
        return saved;
    }

    private AiProcessingJob queueIfAbsent(
            AiOperationType operation, Application app, Document document, User requestedBy
    ) {
        var existing = document == null
                ? jobRepository.findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        operation, app.getId(), app.getSubmissionRevision())
                : jobRepository.findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(operation, document.getId());
        if (existing.isPresent() && REUSABLE.contains(existing.get().getStatus())) {
            return existing.get();
        }
        return queue(operation, app, document, requestedBy);
    }

    private ScoreStatusResponse scoreStatus(AiProcessingJob job) {
        ApplicationScore score = scoreRepository.findById(job.getApplication().getId()).orElse(null);
        ScoreResultResponse result = score != null
                && score.getSubmissionRevision() == job.getSubmissionRevision()
                && score.getJob().getId().equals(job.getId())
                ? new ScoreResultResponse(
                        score.getScore(), score.getRiskCategory(), score.getRecommendedAmount(),
                        readJson(score.getTopFactors()), score.getLlmSummary(),
                        score.getModelName(), score.getModelVersion(), readJson(score.getModelMetadata()),
                        score.getCheckedAt())
                : null;
        return new ScoreStatusResponse(AiTaskResponse.from(job), result);
    }

    private OcrStatusResponse ocrStatus(AiProcessingJob job) {
        DocumentOcrResult ocr = ocrRepository.findById(job.getDocument().getId()).orElse(null);
        OcrResultResponse result = ocr != null && ocr.getJob().getId().equals(job.getId())
                ? new OcrResultResponse(
                        ocr.isReadable(), ocr.getConfidence(), readStringList(ocr.getIssues()),
                        ocr.getExtractedText(), ocr.getCheckedAt())
                : null;
        return new OcrStatusResponse(AiTaskResponse.from(job), result);
    }

    private void createSnapshot(Application app, User requestedBy, CompletenessResult completeness) {
        if (snapshotRepository.existsByApplicationIdAndSubmissionRevision(
                app.getId(), app.getSubmissionRevision())) {
            return;
        }
        List<DocumentResponse> documents = documentRepository.findByApplicationId(app.getId())
                .stream()
                .map(DocumentResponse::from)
                .toList();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", app.getId());
        snapshot.put("applicationNumber", app.getApplicationNumber());
        snapshot.put("applicantId", app.getApplicant().getId());
        snapshot.put("status", app.getStatus());
        snapshot.put("iinOrBin", app.getIinOrBin());
        snapshot.put("region", app.getRegion());
        snapshot.put("productionType", app.getProductionType());
        snapshot.put("activityType", app.getActivityType());
        snapshot.put("applicantCategory", app.getApplicantCategory());
        snapshot.put("landArea", app.getLandArea());
        snapshot.put("requestedAmount", app.getRequestedAmount());
        snapshot.put("documents", documents);
        snapshot.put("completeness", completeness);
        snapshot.put("documentRequirementVersions",
                completenessService.currentRequirementVersions(app));
        snapshot.put("submittedAt", java.time.Instant.now());
        snapshot.put("submissionRevision", app.getSubmissionRevision());

        ApplicationSnapshot entity = new ApplicationSnapshot();
        entity.setApplication(app);
        entity.setSubmissionRevision(app.getSubmissionRevision());
        entity.setCreatedBy(requestedBy);
        entity.setSnapshotJson(writeJson(snapshot));
        snapshotRepository.save(entity);
        auditService.recordDomain(requestedBy, "APPLICATION_SNAPSHOT_CREATED", "APPLICATION",
                app.getId().toString(), null, Map.of(
                        "applicationId", app.getId(),
                        "submissionRevision", app.getSubmissionRevision()));
    }

    private OcrProcessingResponse ocrProcessing(
            List<Document> documents,
            List<AiProcessingJob> jobs,
            List<kz.afm.kendala.application.enums.DocumentType> requiredTypes
    ) {
        List<Document> requiredDocuments = requiredTypes.stream()
                .map(type -> documents.stream()
                        .filter(document -> document.getDocumentType() == type)
                        .max(Comparator.comparing(Document::getCreatedAt))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (requiredDocuments.isEmpty()) {
            return new OcrProcessingResponse("SKIPPED", 0, 0, 0);
        }
        List<AiProcessingJob> ocrJobs = requiredDocuments.stream()
                .map(document -> jobs.stream()
                        .filter(job -> job.getOperationType() == AiOperationType.OCR
                                && job.getDocument() != null
                                && job.getDocument().getId().equals(document.getId()))
                        .max(Comparator.comparing(AiProcessingJob::getCreatedAt))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        int completed = (int) ocrJobs.stream()
                .filter(job -> job.getStatus() == AiTaskStatus.COMPLETED
                        && ocrRepository.findById(job.getDocument().getId())
                        .filter(result -> result.getJob().getId().equals(job.getId()) && result.isReadable())
                        .isPresent())
                .count();
        int failed = (int) ocrJobs.stream()
                .filter(job -> isCriticalFailureStatus(job.getStatus())
                        || (job.getStatus() == AiTaskStatus.COMPLETED
                        && ocrRepository.findById(job.getDocument().getId())
                        .filter(result -> result.getJob().getId().equals(job.getId()) && result.isReadable())
                        .isEmpty()))
                .count();
        return new OcrProcessingResponse(
                failed > 0 ? "FAILED" : aggregateStatus(ocrJobs, requiredDocuments.size()),
                requiredDocuments.size(),
                completed,
                failed
        );
    }

    private boolean requiredOcrReady(
            List<kz.afm.kendala.application.enums.DocumentType> requiredTypes,
            List<Document> documents
    ) {
        return requiredTypes.stream().allMatch(type -> documents.stream()
                .filter(document -> document.getDocumentType() == type)
                .max(Comparator.comparing(Document::getCreatedAt))
                .map(document -> ocrRepository.findById(document.getId())
                        .filter(result -> result.isReadable()
                                && result.getJob().getStatus() == AiTaskStatus.COMPLETED)
                        .isPresent())
                .orElse(false));
    }

    private DuplicateProcessingResponse duplicateProcessing(AiProcessingJob job) {
        if (job == null) {
            return new DuplicateProcessingResponse("NOT_STARTED", null, null, null, List.of(), List.of());
        }
        JsonNode result = readJson(job.getResultJson());
        return new DuplicateProcessingResponse(
                job.getStatus().name(),
                result.has("hasDuplicates") ? result.get("hasDuplicates").asBoolean() : null,
                textOrNull(result, "duplicateType"),
                textOrNull(result, "matchedApplicationId"),
                stringArray(result.path("flags")),
                stringArray(result.path("anomalies"))
        );
    }

    private ScoringProcessingResponse scoringProcessing(AiProcessingJob job, UUID applicationId) {
        ApplicationScore score = scoreRepository.findById(applicationId)
                .filter(candidate -> job != null
                        && candidate.getSubmissionRevision() == job.getSubmissionRevision())
                .orElse(null);
        String status = job == null ? "NOT_STARTED" : job.getStatus().name();
        if (score == null) {
            return new ScoringProcessingResponse(status, null, null, null, null, null, objectMapper.createArrayNode());
        }
        return new ScoringProcessingResponse(
                status,
                score.getScore(),
                score.getRiskCategory(),
                score.getModelName(),
                score.getModelVersion(),
                score.getRecommendedAmount(),
                readJson(score.getTopFactors())
        );
    }

    private LlmConclusionProcessingResponse llmProcessing(AiProcessingJob job, UUID applicationId) {
        ApplicationScore score = scoreRepository.findById(applicationId)
                .filter(candidate -> job != null
                        && candidate.getSubmissionRevision() == job.getSubmissionRevision())
                .orElse(null);
        if (job == null) {
            String text = score == null ? null : score.getLlmSummary();
            return new LlmConclusionProcessingResponse(
                    text == null || text.isBlank() ? "NOT_STARTED" : "COMPLETED",
                    text,
                    null,
                    null
            );
        }
        JsonNode result = readJson(job.getResultJson());
        return new LlmConclusionProcessingResponse(
                job.getStatus().name(),
                textOrNull(result, "text"),
                job.getErrorCode(),
                job.getErrorMessage()
        );
    }

    private AiProcessingJob latest(List<AiProcessingJob> jobs, AiOperationType operation) {
        AiProcessingJob latest = null;
        for (AiProcessingJob job : jobs) {
            if (job.getOperationType() == operation) {
                latest = job;
            }
        }
        return latest;
    }

    private String aggregateStatus(List<AiProcessingJob> jobs, int expectedCount) {
        if (jobs.isEmpty()) {
            return "NOT_STARTED";
        }
        if (jobs.stream().anyMatch(job -> job.getStatus() == AiTaskStatus.PROCESSING)) {
            return "PROCESSING";
        }
        if (jobs.stream().anyMatch(job -> job.getStatus() == AiTaskStatus.PENDING)) {
            return "PENDING";
        }
        if (jobs.stream().anyMatch(job -> job.getStatus() == AiTaskStatus.FAILED_RETRYABLE)) {
            return "PROCESSING";
        }
        if (jobs.size() < expectedCount) {
            return "PENDING";
        }
        if (jobs.stream().anyMatch(job -> isFailureStatus(job.getStatus()))) {
            return "FAILED";
        }
        return "COMPLETED";
    }

    private String overallStatus(
            OcrProcessingResponse ocr,
            DuplicateProcessingResponse duplicate,
            ScoringProcessingResponse scoring,
            LlmConclusionProcessingResponse llm,
            List<AiProcessingJob> jobs
    ) {
        if ("FAILED".equals(ocr.status())
                || "FAILED".equals(duplicate.status())
                || "FAILED_TERMINAL".equals(duplicate.status())
                || "FAILED".equals(scoring.status())
                || "FAILED_TERMINAL".equals(scoring.status())) {
            return "FAILED";
        }
        boolean mandatoryCompleted = ("COMPLETED".equals(ocr.status()) || "SKIPPED".equals(ocr.status()))
                && "COMPLETED".equals(duplicate.status())
                && "COMPLETED".equals(scoring.status());
        if (mandatoryCompleted) {
            if (Boolean.TRUE.equals(duplicate.hasDuplicates())
                    || "FAILED_OPTIONAL".equals(llm.status())) {
                return "COMPLETED_WITH_WARNINGS";
            }
            return "COMPLETED";
        }
        if (isActiveStatus(ocr.status())
                || isActiveStatus(duplicate.status())
                || isActiveStatus(scoring.status())) {
            return "PROCESSING";
        }
        return jobs.isEmpty() ? "NOT_STARTED" : "PENDING";
    }

    private boolean isActiveStatus(String status) {
        return "PENDING".equals(status)
                || "PROCESSING".equals(status)
                || "FAILED_RETRYABLE".equals(status);
    }

    private void requireApplicationAccess(Application app, User user) {
        if (COMMISSION_ROLES.contains(user.getRole())) {
            if (!AI_VIEWABLE_STATUSES.contains(app.getStatus())) throw new NotFoundException("Заявка не найдена");
        } else if (user.getRole() != UserRole.APPLICANT
                || !app.getApplicant().getId().equals(user.getId())) {
            throw new NotFoundException("Заявка не найдена");
        }
    }

    private void requireDocumentAccess(Document document, User user) {
        requireApplicationAccess(document.getApplication(), user);
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored AI JSON is invalid", e);
        }
    }

    private List<String> readStringList(String value) {
        try {
            return objectMapper.readerForListOf(String.class).readValue(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored OCR JSON is invalid", e);
        }
    }

    private boolean isFailureStatus(AiTaskStatus status) {
        return status == AiTaskStatus.FAILED
                || status == AiTaskStatus.FAILED_RETRYABLE
                || status == AiTaskStatus.FAILED_TERMINAL;
    }

    private boolean isCriticalFailureStatus(AiTaskStatus status) {
        return status == AiTaskStatus.FAILED || status == AiTaskStatus.FAILED_TERMINAL;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored workflow JSON is invalid", e);
        }
    }

    private List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
