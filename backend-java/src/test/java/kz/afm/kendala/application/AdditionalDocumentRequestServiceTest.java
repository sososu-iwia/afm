package kz.afm.kendala.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.application.entity.AdditionalDocumentRequest;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.AdditionalDocumentRequestRepository;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.service.AdditionalDocumentRequestService;
import kz.afm.kendala.application.service.ApplicationStatusPolicy;
import kz.afm.kendala.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdditionalDocumentRequestServiceTest {
    private AdditionalDocumentRequestRepository repository;
    private AdditionalDocumentRequestService service;

    @BeforeEach
    void setUp() {
        repository = mock(AdditionalDocumentRequestRepository.class);
        service = new AdditionalDocumentRequestService(
                repository,
                mock(ApplicationRepository.class),
                mock(ApplicationStatusPolicy.class),
                mock(AuditService.class)
        );
    }

    @Test
    void createsOpenRequestWithDeduplicatedTypesAndSourceRevision() {
        Application application = application(1);
        User secretary = user(UserRole.SECRETARY);
        when(repository.findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
                application.getId(), AdditionalDocumentRequestStatus.OPEN)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            AdditionalDocumentRequest request = invocation.getArgument(0);
            ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
            return request;
        });

        AdditionalDocumentRequest result = service.create(
                application,
                secretary,
                "  Нужна банковская выписка  ",
                List.of(DocumentType.BANK_STATEMENT, DocumentType.BANK_STATEMENT)
        );

        assertThat(result.getSourceSubmissionRevision()).isEqualTo(1);
        assertThat(result.getRequestedByRole()).isEqualTo(UserRole.SECRETARY);
        assertThat(result.getRequestedDocumentTypes()).containsExactly(DocumentType.BANK_STATEMENT);
        assertThat(result.getComment()).isEqualTo("Нужна банковская выписка");
        assertThat(result.getStatus()).isEqualTo(AdditionalDocumentRequestStatus.OPEN);
    }

    @Test
    void fulfillsOpenRequestOnlyForSuccessfulLaterRevision() {
        Application application = application(2);
        AdditionalDocumentRequest request = new AdditionalDocumentRequest();
        ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
        request.setApplication(application);
        request.setSourceSubmissionRevision(1);
        request.setStatus(AdditionalDocumentRequestStatus.OPEN);
        request.setRequestedDocumentTypes(java.util.Set.of(DocumentType.BANK_STATEMENT));
        when(repository.findOpenLockedByApplicationId(application.getId())).thenReturn(Optional.of(request));

        service.fulfillOpenRequest(application, 2);

        assertThat(request.getStatus()).isEqualTo(AdditionalDocumentRequestStatus.FULFILLED);
        assertThat(request.getFulfilledAt()).isNotNull();
        assertThat(request.getFulfilledBySubmissionRevision()).isEqualTo(2);
    }

    private Application application(int revision) {
        Application application = new Application();
        ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
        application.setSubmissionRevision(revision);
        return application;
    }

    private User user(UserRole role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}
