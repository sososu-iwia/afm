package kz.afm.kendala.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.ai.AiOrchestrationService;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import kz.afm.kendala.application.service.ApplicationMapper;
import kz.afm.kendala.application.service.ApplicationNumberGenerator;
import kz.afm.kendala.application.service.ApplicationService;
import kz.afm.kendala.application.service.ApplicationStatusPolicy;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.notification.NotificationService;
import kz.afm.kendala.observability.DomainMetrics;
import org.junit.jupiter.api.Test;

class ApplicationServiceTest {

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicationStatusHistoryRepository historyRepository = mock(ApplicationStatusHistoryRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final ApplicationNumberGenerator numberGenerator = new ApplicationNumberGenerator(applicationRepository);
    private final ApplicationCompletenessService completenessService = mock(ApplicationCompletenessService.class);
    private final AiOrchestrationService aiOrchestrationService = mock(AiOrchestrationService.class);
    private final ApplicationService service = new ApplicationService(
            applicationRepository,
            historyRepository,
            documentRepository,
            numberGenerator,
            new ApplicationMapper(),
            completenessService,
            aiOrchestrationService,
            mock(NotificationService.class),
            mock(AuditService.class),
            mock(DomainMetrics.class),
            new ApplicationStatusPolicy()
    );

    @Test
    void rejectsSubmitForNonDraftApplication() {
        User user = user();
        Application application = application(user, ApplicationStatus.SUBMITTED);
        when(applicationRepository.findLockedById(application.getId()))
                .thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.submit(application.getId(), user))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Отправить можно только черновик");
    }

    @Test
    void firstSubmitCreatesRevisionOne() {
        User user = user();
        Application application = application(user, ApplicationStatus.DRAFT);
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));
        when(completenessService.check(application))
                .thenReturn(new CompletenessResult(true, List.of(), List.of()));

        var response = service.submit(application.getId(), user);

        assertThat(response.submissionRevision()).isEqualTo(1);
        assertThat(application.getSubmissionRevision()).isEqualTo(1);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(aiOrchestrationService).startAutomaticWorkflow(
                org.mockito.ArgumentMatchers.eq(application),
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.any(CompletenessResult.class));
    }

    @Test
    void resubmitAfterDocumentRequestCreatesNextRevision() {
        User user = user();
        Application application = application(user, ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED);
        application.setSubmissionRevision(1);
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));
        when(completenessService.check(application))
                .thenReturn(new CompletenessResult(true, List.of(), List.of()));

        var response = service.submit(application.getId(), user);

        assertThat(response.submissionRevision()).isEqualTo(2);
        assertThat(application.getSubmissionRevision()).isEqualTo(2);
    }

    @Test
    void submittedCannotReceiveFinalDecisionButInReviewCan() {
        ApplicationStatusPolicy policy = new ApplicationStatusPolicy();
        Application application = application(user(), ApplicationStatus.SUBMITTED);
        assertThatThrownBy(() -> policy.requireDecidable(application))
                .isInstanceOf(BusinessRuleException.class);

        application.setStatus(ApplicationStatus.IN_REVIEW);
        policy.requireDecidable(application);
    }

    @Test
    void masksForeignOrMissingApplicationAsNotFound() {
        User user = user();
        UUID applicationId = UUID.randomUUID();
        when(applicationRepository.findByIdAndApplicantId(applicationId, user.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(applicationId, user))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void withdrawWritesHistory() {
        User user = user();
        Application application = application(user, ApplicationStatus.DRAFT);
        when(applicationRepository.findLockedById(application.getId()))
                .thenReturn(Optional.of(application));

        service.withdraw(application.getId(), user);

        verify(historyRepository).save(org.mockito.ArgumentMatchers.argThat(history ->
                history.getPreviousStatus() == ApplicationStatus.DRAFT
                        && history.getNewStatus() == ApplicationStatus.WITHDRAWN
        ));
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("+77000000999");
        user.setFullName("Unit User");
        user.setRole(UserRole.APPLICANT);
        return user;
    }

    private Application application(User user, ApplicationStatus status) {
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setApplicant(user);
        application.setApplicationNumber("KD2-UNIT");
        application.setStatus(status);
        return application;
    }
}
