package kz.afm.kendala.application.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kz.afm.kendala.application.dto.ApplicationCreateRequest;
import kz.afm.kendala.application.dto.ApplicationResponse;
import kz.afm.kendala.application.dto.ApplicationUpdateRequest;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.StatusHistoryResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.ApplicationStatusHistory;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.ai.AiOrchestrationService;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.PaginationPolicy;
import kz.afm.kendala.common.exception.ApplicationIncompleteException;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.notification.NotificationService;
import kz.afm.kendala.observability.DomainMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final DocumentRepository documentRepository;
    private final ApplicationNumberGenerator numberGenerator;
    private final ApplicationMapper mapper;
    private final ApplicationCompletenessService completenessService;
    private final AiOrchestrationService aiOrchestrationService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final DomainMetrics metrics;
    private final ApplicationStatusPolicy statusPolicy;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository historyRepository,
            DocumentRepository documentRepository,
            ApplicationNumberGenerator numberGenerator,
            ApplicationMapper mapper,
            ApplicationCompletenessService completenessService,
            AiOrchestrationService aiOrchestrationService,
            NotificationService notificationService,
            AuditService auditService,
            DomainMetrics metrics,
            ApplicationStatusPolicy statusPolicy
    ) {
        this.applicationRepository = applicationRepository;
        this.historyRepository = historyRepository;
        this.documentRepository = documentRepository;
        this.numberGenerator = numberGenerator;
        this.mapper = mapper;
        this.completenessService = completenessService;
        this.aiOrchestrationService = aiOrchestrationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.statusPolicy = statusPolicy;
    }

    @Transactional
    public ApplicationResponse create(ApplicationCreateRequest request, User currentUser) {
        statusPolicy.requireApplicant(currentUser);
        Application application = new Application();
        application.setApplicationNumber(numberGenerator.generate());
        application.setApplicant(currentUser);
        application.setStatus(ApplicationStatus.DRAFT);
        applyFields(
                application,
                request.iinOrBin(),
                request.region(),
                request.productionType(),
                request.landArea(),
                request.requestedAmount(),
                request.activityType(),
                request.applicantCategory()
        );

        Application saved = applicationRepository.save(application);
        saveStatusHistory(saved, null, ApplicationStatus.DRAFT, currentUser, "CREATE", "Заявка создана");
        ApplicationResponse response = mapper.toResponse(saved);
        auditService.recordDomain(currentUser, "APPLICATION_CREATED", "APPLICATION",
                saved.getId().toString(), null, response);
        metrics.increment("application.created");
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> findAll(User currentUser, int page, int size) {
        Pageable pageable = PageRequest.of(
                PaginationPolicy.zeroBased(page),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.ASC, "id"))
        );
        Page<Application> result = applicationRepository.findByApplicantId(currentUser.getId(), pageable);
        List<UUID> applicationIds = result.getContent().stream()
                .map(Application::getId)
                .toList();
        Map<UUID, List<DocumentResponse>> documentsByApplication = applicationIds.isEmpty()
                ? Map.of()
                : documentRepository.findByApplicationIdIn(applicationIds)
                .stream()
                .map(DocumentResponse::from)
                .collect(Collectors.groupingBy(DocumentResponse::applicationId));
        return new PageResponse<>(
                result.getContent().stream()
                        .map(application -> mapper.toResponse(
                                application,
                                documentsByApplication.getOrDefault(application.getId(), List.of())))
                        .toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID id, User currentUser) {
        Application application = getOwnedApplication(id, currentUser);
        List<DocumentResponse> documents = documentRepository.findByApplicationId(application.getId())
                .stream()
                .map(DocumentResponse::from)
                .toList();
        return mapper.toResponse(application, documents);
    }

    @Transactional
    public ApplicationResponse update(UUID id, ApplicationUpdateRequest request, User currentUser) {
        Application application = getOwnedApplicationLocked(id, currentUser);
        statusPolicy.requireEditableByApplicant(application, currentUser);
        ApplicationResponse previous = mapper.toResponse(application);
        applyFields(
                application,
                request.iinOrBin(),
                request.region(),
                request.productionType(),
                request.landArea(),
                request.requestedAmount(),
                request.activityType(),
                request.applicantCategory()
        );
        ApplicationResponse response = mapper.toResponse(application);
        auditService.recordDomain(currentUser, "APPLICATION_UPDATED", "APPLICATION",
                application.getId().toString(), previous, response);
        return response;
    }

    @Transactional
    public ApplicationResponse submit(UUID id, User currentUser) {
        Application application = getOwnedApplicationLocked(id, currentUser);
        statusPolicy.requireSubmittableByApplicant(application, currentUser);
        var completeness = completenessService.check(application);
        if (!completeness.complete()) {
            throw new ApplicationIncompleteException(completeness.missingFields(), completeness.missingDocuments());
        }
        ApplicationResponse previousValue = mapper.toResponse(application);
        ApplicationStatus previous = application.getStatus();
        application.setSubmissionRevision(application.getSubmissionRevision() + 1);
        application.setStatus(ApplicationStatus.SUBMITTED);
        saveStatusHistory(application, previous, ApplicationStatus.SUBMITTED, currentUser, "SUBMIT", "Заявка отправлена");
        ApplicationResponse response = mapper.toResponse(application);
        auditService.recordDomain(currentUser, "APPLICATION_SUBMITTED", "APPLICATION",
                application.getId().toString(), previousValue, response);
        auditService.recordDomain(currentUser, "APPLICATION_SUBMISSION_REVISION_CREATED", "APPLICATION",
                application.getId().toString(), null, Map.of(
                        "applicationId", application.getId(),
                        "submissionRevision", application.getSubmissionRevision()));
        aiOrchestrationService.startAutomaticWorkflow(application, currentUser, completeness);
        notificationService.enqueueSubmitted(application);
        metrics.increment("application.submitted");
        return response;
    }

    @Transactional(readOnly = true)
    public CompletenessResult getCompleteness(UUID id, User currentUser) {
        Application application = getOwnedApplication(id, currentUser);
        return completenessService.check(application);
    }

    @Transactional
    public ApplicationResponse withdraw(UUID id, User currentUser) {
        Application application = getOwnedApplicationLocked(id, currentUser);
        statusPolicy.requireWithdrawableByApplicant(application, currentUser);
        ApplicationResponse previousValue = mapper.toResponse(application);
        ApplicationStatus previous = application.getStatus();
        application.setStatus(ApplicationStatus.WITHDRAWN);
        saveStatusHistory(application, previous, ApplicationStatus.WITHDRAWN, currentUser, "WITHDRAW", "Заявка отозвана");
        ApplicationResponse response = mapper.toResponse(application);
        auditService.recordDomain(currentUser, "APPLICATION_WITHDRAWN", "APPLICATION",
                application.getId().toString(), previousValue, response);
        return response;
    }

    @Transactional
    public void deleteDraft(UUID id, User currentUser) {
        Application application = getOwnedApplication(id, currentUser);
        if (application.getStatus() != ApplicationStatus.DRAFT) {
            throw new BusinessRuleException("Удалить можно только черновик");
        }
        documentRepository.deleteAll(documentRepository.findByApplicationId(application.getId()));
        historyRepository.deleteByApplicationId(application.getId());
        applicationRepository.delete(application);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getStatusHistory(UUID id, User currentUser) {
        Application application = getOwnedApplication(id, currentUser);
        return historyRepository.findByApplicationIdOrderByCreatedAtAsc(application.getId())
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Application getOwnedApplication(UUID id, User currentUser) {
        return applicationRepository.findByIdAndApplicantId(id, currentUser.getId())
                .orElseThrow(() -> new NotFoundException(
                        "Заявка не найдена",
                        "error.application-not-found"
                ));
    }

    private Application getOwnedApplicationLocked(UUID id, User currentUser) {
        Application application = applicationRepository.findLockedById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Заявка не найдена",
                        "error.application-not-found"
                ));
        statusPolicy.requireOwner(application, currentUser);
        return application;
    }

    private void applyFields(Application application, String iinOrBin, String region, String productionType,
                             java.math.BigDecimal landArea, java.math.BigDecimal requestedAmount,
                             ActivityType activityType, ApplicantCategory applicantCategory) {
        application.setIinOrBin(iinOrBin);
        application.setRegion(region);
        application.setProductionType(productionType);
        application.setLandArea(landArea);
        application.setRequestedAmount(requestedAmount);
        if (activityType != null || application.getActivityType() == null) {
            application.setActivityType(activityType != null ? activityType : ActivityType.OTHER);
        }
        if (applicantCategory != null || application.getApplicantCategory() == null) {
            application.setApplicantCategory(
                    applicantCategory != null ? applicantCategory : ApplicantCategory.OTHER
            );
        }
    }

    private void saveStatusHistory(Application application, ApplicationStatus previousStatus, ApplicationStatus newStatus,
                                   User changedBy, String reason, String comment) {
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(application);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        history.setComment(comment);
        historyRepository.save(history);
    }
}
