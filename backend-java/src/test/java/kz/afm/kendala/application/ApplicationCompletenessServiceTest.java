package kz.afm.kendala.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.ApplicationSnapshot;
import kz.afm.kendala.ai.ApplicationSnapshotRepository;
import kz.afm.kendala.ai.DocumentOcrResultRepository;
import kz.afm.kendala.application.config.AppDocumentsProperties;
import kz.afm.kendala.application.entity.AdditionalDocumentRequest;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.repository.AdditionalDocumentRequestRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.DocumentRequirementRepository;
import kz.afm.kendala.application.service.ApplicationCompletenessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationCompletenessServiceTest {
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentRequirementRepository requirementRepository = mock(DocumentRequirementRepository.class);
    private final DocumentOcrResultRepository ocrRepository = mock(DocumentOcrResultRepository.class);
    private final AiProcessingJobRepository jobRepository = mock(AiProcessingJobRepository.class);
    private final AdditionalDocumentRequestRepository requestRepository =
            mock(AdditionalDocumentRequestRepository.class);
    private final ApplicationSnapshotRepository snapshotRepository = mock(ApplicationSnapshotRepository.class);
    private ApplicationCompletenessService service;

    @BeforeEach
    void setUp() {
        AppDocumentsProperties properties = new AppDocumentsProperties();
        properties.setRequiredTypes(List.of());
        service = new ApplicationCompletenessService(
                documentRepository,
                requirementRepository,
                properties,
                ocrRepository,
                jobRepository,
                requestRepository,
                snapshotRepository,
                new ObjectMapper()
        );
    }

    @Test
    void openRequestAddsMissingRequestedDocumentTypes() {
        Application application = completeApplication(ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED, 1);
        AdditionalDocumentRequest request = new AdditionalDocumentRequest();
        request.setRequestedDocumentTypes(Set.of(DocumentType.BANK_STATEMENT));
        request.setStatus(AdditionalDocumentRequestStatus.OPEN);
        when(documentRepository.findByApplicationId(application.getId())).thenReturn(List.of());
        when(requestRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
                application.getId(), AdditionalDocumentRequestStatus.OPEN))
                .thenReturn(Optional.of(request));

        var result = service.check(application);

        assertThat(result.complete()).isFalse();
        assertThat(result.missingDocuments()).containsExactly("BANK_STATEMENT");
        assertThat(result.requiredTypes()).containsExactly(DocumentType.BANK_STATEMENT);
    }

    @Test
    void submittedRevisionUsesSnapshotRequirementsInsteadOfMutableCurrentRules() {
        Application application = completeApplication(ApplicationStatus.SUBMITTED, 2);
        ApplicationSnapshot snapshot = new ApplicationSnapshot();
        snapshot.setSnapshotJson("""
                {"completeness":{"requiredTypes":["TAX_CERTIFICATE"]}}
                """);
        when(snapshotRepository.findByApplicationIdAndSubmissionRevision(application.getId(), 2))
                .thenReturn(Optional.of(snapshot));
        when(documentRepository.findByApplicationId(application.getId())).thenReturn(List.of());

        var result = service.check(application);

        assertThat(result.requiredTypes()).containsExactly(DocumentType.TAX_CERTIFICATE);
        assertThat(result.missingDocuments()).containsExactly("TAX_CERTIFICATE");
    }

    private Application completeApplication(ApplicationStatus status, int revision) {
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setStatus(status);
        application.setSubmissionRevision(revision);
        application.setIinOrBin("123456789012");
        application.setRegion("Akmola");
        application.setProductionType("Wheat");
        application.setLandArea(new BigDecimal("100"));
        application.setRequestedAmount(new BigDecimal("1000000"));
        return application;
    }
}
