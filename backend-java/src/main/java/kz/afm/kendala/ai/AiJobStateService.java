package kz.afm.kendala.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.ai.dto.AiDuplicateApplication;
import kz.afm.kendala.ai.dto.AiDuplicateCheckRequest;
import kz.afm.kendala.ai.dto.AiDuplicateCheckResponse;
import kz.afm.kendala.ai.dto.AiLlmConclusionRequest;
import kz.afm.kendala.ai.dto.AiLlmConclusionResponse;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreRequest;
import kz.afm.kendala.ai.dto.AiScoreResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.ApplicationStatusHistory;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import kz.afm.kendala.application.service.AdditionalDocumentRequestService;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.notification.NotificationService;
import kz.afm.kendala.observability.DomainMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiJobStateService {
    private static final Set<AiTaskStatus> REUSABLE = EnumSet.of(
            AiTaskStatus.PENDING,
            AiTaskStatus.PROCESSING,
            AiTaskStatus.FAILED_RETRYABLE,
            AiTaskStatus.COMPLETED,
            AiTaskStatus.FAILED_OPTIONAL,
            AiTaskStatus.SKIPPED
    );
    private static final Set<AiTaskStatus> MANUALLY_RETRYABLE = EnumSet.of(
            AiTaskStatus.FAILED,
            AiTaskStatus.FAILED_RETRYABLE,
            AiTaskStatus.FAILED_TERMINAL,
            AiTaskStatus.FAILED_OPTIONAL
    );
    private static final Set<ApplicationStatus> DUPLICATE_CANDIDATE_STATUSES = EnumSet.of(
            ApplicationStatus.DRAFT,
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.IN_REVIEW,
            ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED
    );
    private static final Set<ApplicationStatus> FINAL_STATUSES = EnumSet.of(
            ApplicationStatus.APPROVED, ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN);

    private final AiProcessingJobRepository jobRepository;
    private final ApplicationScoreRepository scoreRepository;
    private final DocumentOcrResultRepository ocrRepository;
    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationCompletenessService completenessService;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainMetrics metrics;
    private final AdditionalDocumentRequestService documentRequestService;
    private final int maxAttempts;
    private final String workerId = java.util.UUID.randomUUID().toString();
    private final long leaseSeconds;
    /** Язык текстового заключения ИИ: ru | kz | en. */
    private final String conclusionLanguage;

    public AiJobStateService(
            AiProcessingJobRepository jobRepository,
            ApplicationScoreRepository scoreRepository,
            DocumentOcrResultRepository ocrRepository,
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            ApplicationCompletenessService completenessService,
            ApplicationStatusHistoryRepository historyRepository,
            NotificationService notificationService,
            AuditService auditService,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher,
            DomainMetrics metrics,
            AdditionalDocumentRequestService documentRequestService,
            @Value("${app.ai.max-attempts:3}") int maxAttempts,
            @Value("${app.ai.lease-seconds:120}") long leaseSeconds,
            @Value("${app.ai.conclusion-language:ru}") String conclusionLanguage
    ) {
        this.jobRepository = jobRepository;
        this.scoreRepository = scoreRepository;
        this.ocrRepository = ocrRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.completenessService = completenessService;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.documentRequestService = documentRequestService;
        this.maxAttempts = maxAttempts;
        this.leaseSeconds = leaseSeconds;
        this.conclusionLanguage = conclusionLanguage;
    }

    @Transactional
    public AiWorkItem start(UUID jobId) {
        AiProcessingJob job = getLockedJob(jobId);
        if ((job.getStatus() != AiTaskStatus.PENDING
                && job.getStatus() != AiTaskStatus.FAILED
                && job.getStatus() != AiTaskStatus.FAILED_RETRYABLE)
                || job.getAttemptCount() >= job.getMaxAttempts()
                || job.getNextAttemptAt().isAfter(Instant.now())) {
            return null;
        }
        Application lockedApplication = applicationRepository.findLockedById(job.getApplication().getId())
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (FINAL_STATUSES.contains(lockedApplication.getStatus())
                || (job.getDocument() == null
                && !Integer.valueOf(lockedApplication.getSubmissionRevision())
                .equals(job.getSubmissionRevision()))) {
            supersede(job, lockedApplication);
            return null;
        }
        job.setStatus(AiTaskStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        job.setLeaseOwner(workerId);
        job.setLeaseUntil(Instant.now().plusSeconds(leaseSeconds));
        if (job.getCorrelationId() == null || job.getCorrelationId().isBlank()) {
            job.setCorrelationId(org.slf4j.MDC.get("correlationId"));
        }

        if (job.getOperationType() == AiOperationType.SCORING) {
            var app = job.getApplication();
            boolean complete = completenessService.check(app).complete();
            AiScoreRequest request = new AiScoreRequest(
                    app.getId(), app.getIinOrBin(), app.getRegion(), app.getProductionType(),
                    app.getLandArea(), app.getRequestedAmount(), complete ? BigDecimal.ONE : BigDecimal.ZERO);
            return new AiWorkItem(jobId, job.getOperationType(), job.getAttemptCount(),
                    job.getMaxAttempts(), request, null, null,
                    null, null, null, null);
        }

        if (job.getOperationType() == AiOperationType.DUPLICATE_CHECK) {
            Application app = job.getApplication();
            List<AiDuplicateApplication> candidates = applicationRepository.findDuplicateCandidates(
                            app.getId(), app.getIinOrBin(), app.getRegion(), app.getProductionType(),
                            DUPLICATE_CANDIDATE_STATUSES)
                    .stream()
                    .map(AiDuplicateApplication::from)
                    .toList();
            return new AiWorkItem(jobId, job.getOperationType(), job.getAttemptCount(),
                    job.getMaxAttempts(), null,
                    new AiDuplicateCheckRequest(AiDuplicateApplication.from(app), candidates), null,
                    null, null, null, null);
        }

        if (job.getOperationType() == AiOperationType.LLM_CONCLUSION) {
            Application app = job.getApplication();
            ApplicationScore score = scoreRepository.findById(app.getId())
                    .orElseThrow(() -> new AiGatewayException(
                            "SCORING_RESULT_MISSING",
                            "Scoring result is required before LLM conclusion",
                            false));
            AiLlmConclusionRequest request = new AiLlmConclusionRequest(
                    app.getId(), app.getRegion(), app.getProductionType(), app.getLandArea(),
                    app.getRequestedAmount(), score.getScore(), score.getRiskCategory(),
                    score.getRecommendedAmount(), readJson(score.getTopFactors()),
                    conclusionLanguage);
            return new AiWorkItem(jobId, job.getOperationType(), job.getAttemptCount(),
                    job.getMaxAttempts(), null, null, request,
                    null, null, null, null);
        }

        var document = job.getDocument();
        return new AiWorkItem(jobId, job.getOperationType(), job.getAttemptCount(),
                job.getMaxAttempts(), null, null, null,
                document.getId(), document.getStorageKey(), document.getOriginalFileName(),
                document.getContentType());
    }

    @Transactional
    public void recordAttempt(UUID jobId) {
        AiProcessingJob job = getLockedJob(jobId);
        job.setAttemptCount(job.getAttemptCount() + 1);
    }

    @Transactional
    public void scheduleNextAttempt(UUID jobId, Instant nextAttemptAt) {
        AiProcessingJob job = getLockedJob(jobId);
        job.setNextAttemptAt(nextAttemptAt);
    }

    @Transactional
    public void retryableFailure(UUID jobId, String code, String message, Instant nextAttemptAt) {
        AiProcessingJob job = getLockedJob(jobId);
        job.setStatus(AiTaskStatus.FAILED_RETRYABLE);
        job.setErrorCode(code);
        job.setErrorMessage(sanitizeError(message, "AI processing failed"));
        job.setNextAttemptAt(nextAttemptAt);
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        auditService.record(
                "system",
                "SYSTEM",
                "AI_JOB_RETRIED",
                "AI_JOB",
                job.getId().toString(),
                null,
                Map.of(
                        "applicationId", job.getApplication().getId(),
                        "operationType", job.getOperationType(),
                        "attempt", job.getAttemptCount(),
                        "nextAttemptAt", nextAttemptAt
                ),
                "system",
                job.getCorrelationId(),
                "FAILURE",
                "WORKER",
                code
        );
    }

    @Transactional
    public void completeScore(UUID jobId, AiScoreResponse response) {
        AiProcessingJob job = getLockedJob(jobId);
        if (!job.getApplication().getId().equals(response.applicationId())) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "AI response applicationId mismatch", false);
        }
        Application application = lockCurrentWorkflow(job);
        if (application == null) {
            return;
        }
        validateScoreResponse(application, response);
        ApplicationScore score = scoreRepository.findById(response.applicationId())
                .orElseGet(ApplicationScore::new);
        score.setApplicationId(response.applicationId());
        score.setJob(job);
        score.setSubmissionRevision(job.getSubmissionRevision());
        score.setScore(response.score());
        score.setRiskCategory(response.riskCategory());
        score.setRecommendedAmount(response.recommendedAmount());
        score.setTopFactors(writeJson(response.topFactors() == null
                ? objectMapper.createArrayNode()
                : response.topFactors()));
        score.setLlmSummary(response.llmSummary());
        score.setModelName(response.modelName());
        score.setModelVersion(response.modelVersion());
        score.setModelMetadata(writeJson(response.modelMetadata() == null
                ? objectMapper.createObjectNode()
                : response.modelMetadata()));
        score.setCheckedAt(Instant.now());
        scoreRepository.save(score);
        complete(job);
        queueIfAbsent(AiOperationType.LLM_CONCLUSION, application, null);
        tryMarkReadyForReview(application, job.getSubmissionRevision());
    }

    @Transactional
    public void completeOcr(UUID jobId, AiOcrResponse response) {
        AiProcessingJob job = getLockedJob(jobId);
        if (job.getDocument() == null || !job.getDocument().getId().equals(response.documentId())) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "AI response documentId mismatch", false);
        }
        DocumentOcrResult result = ocrRepository.findById(response.documentId())
                .orElseGet(DocumentOcrResult::new);
        result.setDocumentId(response.documentId());
        result.setJob(job);
        result.setReadable(response.readable());
        result.setConfidence(response.confidence() == null ? BigDecimal.ZERO : response.confidence());
        result.setIssues(writeJson(response.issues()));
        result.setExtractedText(response.extractedText() == null ? "" : response.extractedText());
        result.setCheckedAt(Instant.now());
        ocrRepository.save(result);
        complete(job);
        queueScoringWhenOcrReady(job.getApplication());
    }

    @Transactional
    public void completeDuplicateCheck(UUID jobId, AiDuplicateCheckResponse response) {
        AiProcessingJob job = getLockedJob(jobId);
        if (!job.getApplication().getId().equals(response.applicationId())) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "AI response applicationId mismatch", false);
        }
        Application application = lockCurrentWorkflow(job);
        if (application == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hasDuplicates", response.hasDuplicates());
        payload.put("duplicateType", response.duplicateType());
        payload.put("matchedApplicationId", response.matchedApplicationId());
        payload.put("flags", response.flags() == null ? List.of() : response.flags());
        payload.put("anomalies", response.anomalies() == null ? List.of() : response.anomalies());
        job.setResultJson(writeJson(payload));
        complete(job);
        tryMarkReadyForReview(application, job.getSubmissionRevision());
    }

    @Transactional
    public void completeLlmConclusion(UUID jobId, AiLlmConclusionResponse response) {
        AiProcessingJob job = getLockedJob(jobId);
        if (!job.getApplication().getId().equals(response.applicationId())) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "AI response applicationId mismatch", false);
        }
        Application application = lockCurrentWorkflow(job);
        if (application == null) {
            return;
        }
        String text = response.text() == null ? "" : response.text();
        job.setResultJson(writeJson(Map.of("text", text)));
        scoreRepository.findById(response.applicationId())
                .filter(score -> score.getSubmissionRevision() == job.getSubmissionRevision())
                .ifPresent(score -> {
            score.setLlmSummary(text);
            scoreRepository.save(score);
        });
        complete(job);
    }

    @Transactional
    public void fail(UUID jobId, String code, String message) {
        AiProcessingJob job = getLockedJob(jobId);
        if (job.getDocument() == null && lockCurrentWorkflow(job) == null) {
            return;
        }
        job.setStatus(AiTaskStatus.FAILED_TERMINAL);
        job.setErrorCode(code);
        job.setErrorMessage(sanitizeError(message, "AI processing failed"));
        job.setCompletedAt(Instant.now());
        job.setNextAttemptAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        metrics.increment(metric(job.getOperationType(), "failed"));
        if (job.getDocument() == null) {
            auditWorkflowBlocked(job, code);
        }
        auditService.record(
                "system",
                "SYSTEM",
                job.getOperationType() == AiOperationType.SCORING ? "SCORING_FAILED" : "AI_JOB_DEAD",
                "AI_JOB",
                job.getId().toString(),
                null,
                Map.of("operationType", job.getOperationType(), "applicationId", job.getApplication().getId()),
                "system",
                job.getCorrelationId(),
                "FAILURE",
                "WORKER",
                code
        );
    }

    @Transactional
    public void failOptional(UUID jobId, String code, String message) {
        AiProcessingJob job = getLockedJob(jobId);
        if (job.getDocument() == null && lockCurrentWorkflow(job) == null) {
            return;
        }
        job.setStatus(AiTaskStatus.FAILED_OPTIONAL);
        job.setErrorCode(code);
        job.setErrorMessage(sanitizeError(message, "Optional AI processing failed"));
        job.setCompletedAt(Instant.now());
        job.setNextAttemptAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        metrics.increment(metric(job.getOperationType(), "failed_optional"));
        if (job.getOperationType() == AiOperationType.LLM_CONCLUSION) {
            notificationService.enqueueProcessingPartiallyFailed(job.getApplication(), code);
            var payload = new LinkedHashMap<String, Object>();
            payload.put("operationType", job.getOperationType());
            payload.put("errorCode", code);
            auditService.record(
                    "system",
                    "SYSTEM",
                    "AI_OPTIONAL_PROCESSING_FAILED",
                    "APPLICATION",
                    job.getApplication().getId().toString(),
                    null,
                    payload,
                    "system",
                    "system"
            );
            tryMarkReadyForReview(job.getApplication(), job.getSubmissionRevision());
        }
    }

    @Transactional
    public void manualRetry(UUID jobId, kz.afm.kendala.application.entity.User actor) {
        AiProcessingJob job = getLockedJob(jobId);
        Application application = applicationRepository.findLockedById(job.getApplication().getId())
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (FINAL_STATUSES.contains(application.getStatus())) {
            throw new kz.afm.kendala.common.exception.ConflictException(
                    "AI job нельзя повторить после финального решения");
        }
        if (job.getDocument() == null
                && !Integer.valueOf(application.getSubmissionRevision()).equals(job.getSubmissionRevision())) {
            throw new kz.afm.kendala.common.exception.ConflictException(
                    "AI job относится к предыдущей ревизии заявки");
        }
        if (job.getStatus() == AiTaskStatus.PENDING || job.getStatus() == AiTaskStatus.PROCESSING) {
            throw new kz.afm.kendala.common.exception.ConflictException("AI job уже выполняется или ожидает выполнения");
        }
        if (!MANUALLY_RETRYABLE.contains(job.getStatus())) {
            throw new kz.afm.kendala.common.exception.ConflictException("AI job нельзя повторить в текущем статусе");
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
        auditService.recordDomain(actor, "AI_JOB_RETRIED", "AI_JOB",
                job.getId().toString(), null, Map.of(
                        "operationType", job.getOperationType(),
                        "applicationId", job.getApplication().getId()));
        eventPublisher.publishEvent(new AiJobQueuedEvent(job.getId()));
    }

    private void complete(AiProcessingJob job) {
        job.setStatus(AiTaskStatus.COMPLETED);
        job.setErrorCode(null);
        job.setErrorMessage(null);
        job.setCompletedAt(Instant.now());
        job.setNextAttemptAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        metrics.increment(metric(job.getOperationType(), "completed"));
        String action = switch (job.getOperationType()) {
            case OCR -> "OCR_COMPLETED";
            case DUPLICATE_CHECK -> "DUPLICATE_CHECK_COMPLETED";
            case SCORING -> "SCORING_COMPLETED";
            case LLM_CONCLUSION -> "LLM_COMPLETED";
        };
        auditService.record(
                "system",
                "SYSTEM",
                action,
                job.getDocument() == null ? "APPLICATION" : "DOCUMENT",
                job.getDocument() == null
                        ? job.getApplication().getId().toString()
                        : job.getDocument().getId().toString(),
                null,
                Map.of("jobId", job.getId(), "operationType", job.getOperationType()),
                "system",
                job.getCorrelationId()
        );
    }

    private void queueScoringWhenOcrReady(Application application) {
        Application current = applicationRepository.findLockedById(application.getId())
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (current.getStatus() == ApplicationStatus.SUBMITTED
                && completenessService.check(current).complete()) {
            queueIfAbsent(AiOperationType.SCORING, application, null);
        }
    }

    private AiProcessingJob queueIfAbsent(AiOperationType operation, Application application, Document document) {
        var existing = document == null
                ? jobRepository.findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        operation, application.getId(), application.getSubmissionRevision())
                : jobRepository.findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(
                        operation, document.getId());
        if (existing.isPresent() && REUSABLE.contains(existing.get().getStatus())) {
            return existing.get();
        }
        AiProcessingJob job = new AiProcessingJob();
        job.setOperationType(operation);
        job.setApplication(application);
        job.setDocument(document);
        job.setSubmissionRevision(document == null ? application.getSubmissionRevision() : null);
        job.setStatus(AiTaskStatus.PENDING);
        job.setMaxAttempts(maxAttempts);
        job.setCorrelationId(org.slf4j.MDC.get("correlationId"));
        job.setIdempotencyKey(document == null
                ? application.getId() + ":" + application.getSubmissionRevision() + ":" + operation
                : document.getId() + ":OCR");
        AiProcessingJob saved = jobRepository.save(job);
        eventPublisher.publishEvent(new AiJobQueuedEvent(saved.getId()));
        return saved;
    }

    private void tryMarkReadyForReview(Application application, int revision) {
        Application current = applicationRepository.findLockedById(application.getId())
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (current.getStatus() != ApplicationStatus.SUBMITTED
                || current.getSubmissionRevision() != revision
                || !completenessService.check(current).complete()) {
            return;
        }
        AiProcessingJob duplicate = currentJob(current, revision, AiOperationType.DUPLICATE_CHECK);
        AiProcessingJob scoring = currentJob(current, revision, AiOperationType.SCORING);
        ApplicationScore score = scoreRepository.findById(current.getId()).orElse(null);
        if (duplicate == null || duplicate.getStatus() != AiTaskStatus.COMPLETED
                || scoring == null || scoring.getStatus() != AiTaskStatus.COMPLETED
                || score == null || score.getSubmissionRevision() != revision
                || !score.getJob().getId().equals(scoring.getId())) {
            return;
        }
        ApplicationStatus previous = current.getStatus();
        documentRequestService.fulfillOpenRequest(current, revision);
        current.setStatus(ApplicationStatus.IN_REVIEW);

        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(current);
        history.setPreviousStatus(previous);
        history.setNewStatus(ApplicationStatus.IN_REVIEW);
        history.setChangedBy(current.getApplicant());
        history.setReason("AI_READY_FOR_REVIEW");
        history.setComment("Автоматическая проверка завершена, заявка передана комиссии");
        historyRepository.save(history);

        var payload = new LinkedHashMap<String, Object>();
        payload.put("previousStatus", previous);
        payload.put("newStatus", ApplicationStatus.IN_REVIEW);
        payload.put("submissionRevision", revision);
        payload.put("duplicateJobId", duplicate.getId());
        payload.put("scoringJobId", scoring.getId());
        auditService.record(
                "system",
                "SYSTEM",
                "APPLICATION_READY_FOR_REVIEW",
                "APPLICATION",
                current.getId().toString(),
                Map.of("status", previous),
                payload,
                "system",
                "system"
        );
        auditService.record(
                "system", "SYSTEM", "AI_WORKFLOW_READY", "APPLICATION",
                current.getId().toString(), null,
                Map.of("submissionRevision", revision,
                        "duplicateJobId", duplicate.getId(), "scoringJobId", scoring.getId()),
                "system", "system");
        notificationService.enqueueReadyForReview(current);
        metrics.increment("application.ready_for_review");
    }

    private AiProcessingJob currentJob(Application application, int revision, AiOperationType operation) {
        return jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        operation, application.getId(), revision)
                .orElse(null);
    }

    private Application lockCurrentWorkflow(AiProcessingJob job) {
        Application application = applicationRepository.findLockedById(job.getApplication().getId())
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));
        if (FINAL_STATUSES.contains(application.getStatus())
                || !Integer.valueOf(application.getSubmissionRevision()).equals(job.getSubmissionRevision())) {
            supersede(job, application);
            return null;
        }
        return application;
    }

    private void supersede(AiProcessingJob job, Application application) {
        job.setStatus(AiTaskStatus.SKIPPED);
        job.setErrorCode("WORKFLOW_SUPERSEDED");
        job.setErrorMessage("AI workflow is no longer current");
        job.setCompletedAt(Instant.now());
        job.setLeaseOwner(null);
        job.setLeaseUntil(null);
        auditService.record(
                "system", "SYSTEM", "AI_WORKFLOW_SUPERSEDED", "AI_JOB",
                job.getId().toString(), null,
                Map.of("applicationId", application.getId(),
                        "jobRevision", job.getSubmissionRevision() == null ? "document-scoped" : job.getSubmissionRevision(),
                        "currentRevision", application.getSubmissionRevision(),
                        "applicationStatus", application.getStatus()),
                "system", job.getCorrelationId());
    }

    private void auditWorkflowBlocked(AiProcessingJob job, String code) {
        auditService.record(
                "system", "SYSTEM", "AI_WORKFLOW_BLOCKED", "APPLICATION",
                job.getApplication().getId().toString(), null,
                Map.of("submissionRevision", job.getSubmissionRevision(),
                        "jobId", job.getId(), "operationType", job.getOperationType(),
                        "errorCode", code),
                "system", job.getCorrelationId(), "FAILURE", "WORKER", code);
    }

    private AiProcessingJob getLockedJob(UUID id) {
        return jobRepository.findLockedById(id)
                .orElseThrow(() -> new NotFoundException("AI task not found"));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "AI response cannot be persisted", false, e);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException e) {
            throw new AiGatewayException("AI_CONTRACT_ERROR", "Stored AI JSON is invalid", false, e);
        }
    }

    private String metric(AiOperationType operation, String result) {
        return switch (operation) {
            case SCORING -> "scoring." + result;
            case OCR -> "ocr." + result;
            case DUPLICATE_CHECK -> "duplicate_check." + result;
            case LLM_CONCLUSION -> "llm_conclusion." + result;
        };
    }

    private String sanitizeError(String message, String fallback) {
        String safe = message == null || message.isBlank() ? fallback : message.replaceAll("[\\r\\n\\t]", " ").strip();
        if (safe.length() > 1000) {
            safe = safe.substring(0, 1000);
        }
        return safe;
    }

    private void validateScoreResponse(Application application, AiScoreResponse response) {
        if (response.score() == null
                || response.score().compareTo(BigDecimal.ZERO) < 0
                || response.score().compareTo(new BigDecimal("100")) > 0) {
            throw new AiGatewayException(
                    "AI_CONTRACT_ERROR", "AI score must be between 0 and 100", false);
        }
        if (response.riskCategory() == null || response.riskCategory().isBlank()) {
            throw new AiGatewayException(
                    "AI_CONTRACT_ERROR", "AI risk category is required", false);
        }
        if (response.recommendedAmount() != null
                && response.recommendedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new AiGatewayException(
                    "AI_CONTRACT_ERROR", "AI recommended amount cannot be negative", false);
        }
        if (response.recommendedAmount() != null
                && application.getRequestedAmount() != null
                && response.recommendedAmount().compareTo(application.getRequestedAmount()) > 0) {
            throw new AiGatewayException(
                    "AI_CONTRACT_ERROR", "AI recommended amount exceeds requested amount", false);
        }
    }
}
