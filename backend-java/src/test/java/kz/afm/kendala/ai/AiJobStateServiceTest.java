package kz.afm.kendala.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreResponse;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.ApplicationStatusHistory;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import kz.afm.kendala.application.service.AdditionalDocumentRequestService;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.notification.NotificationService;
import kz.afm.kendala.observability.DomainMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AiJobStateServiceTest {

    @Mock AiProcessingJobRepository jobRepository;
    @Mock ApplicationScoreRepository scoreRepository;
    @Mock DocumentOcrResultRepository ocrRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock DocumentRepository documentRepository;
    @Mock ApplicationCompletenessService completenessService;
    @Mock ApplicationStatusHistoryRepository historyRepository;
    @Mock NotificationService notificationService;
    @Mock AuditService auditService;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock DomainMetrics metrics;
    @Mock AdditionalDocumentRequestService documentRequestService;

    private AiJobStateService service;

    @BeforeEach
    void setUp() {
        service = new AiJobStateService(
                jobRepository,
                scoreRepository,
                ocrRepository,
                applicationRepository,
                documentRepository,
                completenessService,
                historyRepository,
                notificationService,
                auditService,
                new ObjectMapper(),
                eventPublisher,
                metrics,
                documentRequestService,
                3,
                120,
                "ru"
        );
    }

    @Test
    void failedMandatoryOcrDoesNotQueueScoring() {
        Application application = submittedApplication();
        AiProcessingJob ocr = job(AiOperationType.OCR, application, newDocument(application), null);
        when(jobRepository.findLockedById(ocr.getId())).thenReturn(Optional.of(ocr));

        service.fail(ocr.getId(), "OCR_UNAVAILABLE", "OCR service is unavailable");

        assertThat(ocr.getStatus()).isEqualTo(AiTaskStatus.FAILED_TERMINAL);
        verify(jobRepository, never()).save(any(AiProcessingJob.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unreadableMandatoryDocumentDoesNotQueueScoringOrBecomeReviewable() {
        Application application = submittedApplication();
        Document document = newDocument(application);
        AiProcessingJob ocr = job(AiOperationType.OCR, application, document, null);
        when(jobRepository.findLockedById(ocr.getId())).thenReturn(Optional.of(ocr));
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));
        when(completenessService.check(application))
                .thenReturn(new CompletenessResult(false, List.of(), List.of()));

        service.completeOcr(ocr.getId(), new AiOcrResponse(
                document.getId(), false, List.of("UNREADABLE"), "", BigDecimal.ZERO, false));

        assertThat(ocr.getStatus()).isEqualTo(AiTaskStatus.COMPLETED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SUBMITTED);
        verify(jobRepository, never()).save(any(AiProcessingJob.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void optionalLlmFailureDoesNotBlockReadyForReview() {
        Application application = submittedApplication();
        AiProcessingJob llm = job(AiOperationType.LLM_CONCLUSION, application, null, 1);
        AiProcessingJob duplicate = job(AiOperationType.DUPLICATE_CHECK, application, null, 1);
        AiProcessingJob scoring = job(AiOperationType.SCORING, application, null, 1);
        duplicate.setStatus(AiTaskStatus.COMPLETED);
        scoring.setStatus(AiTaskStatus.COMPLETED);
        ApplicationScore score = new ApplicationScore();
        score.setApplicationId(application.getId());
        score.setSubmissionRevision(1);
        score.setJob(scoring);

        when(jobRepository.findLockedById(llm.getId())).thenReturn(Optional.of(llm));
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));
        when(completenessService.check(application)).thenReturn(new CompletenessResult(true, List.of(), List.of()));
        when(jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.DUPLICATE_CHECK, application.getId(), 1))
                .thenReturn(Optional.of(duplicate));
        when(jobRepository
                .findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
                        AiOperationType.SCORING, application.getId(), 1))
                .thenReturn(Optional.of(scoring));
        when(scoreRepository.findById(application.getId())).thenReturn(Optional.of(score));

        service.failOptional(llm.getId(), "LLM_UNAVAILABLE", "Optional LLM is unavailable");

        assertThat(llm.getStatus()).isEqualTo(AiTaskStatus.FAILED_OPTIONAL);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.IN_REVIEW);
        verify(documentRequestService).fulfillOpenRequest(application, 1);
        verify(historyRepository).save(any(ApplicationStatusHistory.class));
        verify(notificationService).enqueueReadyForReview(application);
    }

    @Test
    void completedBusinessJobCannotBeRetried() {
        Application application = submittedApplication();
        AiProcessingJob scoring = job(AiOperationType.SCORING, application, null, 1);
        scoring.setStatus(AiTaskStatus.COMPLETED);
        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(jobRepository.findLockedById(scoring.getId())).thenReturn(Optional.of(scoring));
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.manualRetry(scoring.getId(), admin))
                .hasMessageContaining("нельзя повторить");

        assertThat(scoring.getStatus()).isEqualTo(AiTaskStatus.COMPLETED);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectsOutOfRangeScoreWithoutChangingCurrentBusinessScore() {
        Application application = submittedApplication();
        application.setRequestedAmount(new BigDecimal("1000"));
        AiProcessingJob scoring = job(AiOperationType.SCORING, application, null, 1);
        when(jobRepository.findLockedById(scoring.getId())).thenReturn(Optional.of(scoring));
        when(applicationRepository.findLockedById(application.getId())).thenReturn(Optional.of(application));

        AiScoreResponse invalid = new AiScoreResponse(
                application.getId(),
                new BigDecimal("101"),
                "LOW",
                new BigDecimal("900"),
                new ObjectMapper().createArrayNode(),
                null
        );

        assertThatThrownBy(() -> service.completeScore(scoring.getId(), invalid))
                .isInstanceOf(AiGatewayException.class)
                .hasMessageContaining("between 0 and 100");
        verify(scoreRepository, never()).save(any(ApplicationScore.class));
    }

    private Application submittedApplication() {
        User applicant = new User();
        applicant.setId(UUID.randomUUID());
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setApplicant(applicant);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmissionRevision(1);
        return application;
    }

    private Document newDocument(Application application) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setApplication(application);
        return document;
    }

    private AiProcessingJob job(
            AiOperationType operation,
            Application application,
            Document document,
            Integer revision
    ) {
        AiProcessingJob job = new AiProcessingJob();
        job.setOperationType(operation);
        job.setApplication(application);
        job.setDocument(document);
        job.setSubmissionRevision(revision);
        job.setStatus(AiTaskStatus.PROCESSING);
        job.setMaxAttempts(3);
        job.prePersist();
        return job;
    }
}
