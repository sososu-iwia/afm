package kz.afm.kendala.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class NotificationServiceTest {

    @Test
    void repeatedSubmissionEventsAreIdempotentPerRevision() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        NotificationService service = new NotificationService(
                repository, mock(ApplicationEventPublisher.class), 3);
        User applicant = new User();
        applicant.setId(UUID.randomUUID());
        applicant.setPhone("+77000000000");
        Application application = new Application();
        application.setId(UUID.randomUUID());
        application.setApplicationNumber("KD2-20260823-100000");
        application.setApplicant(applicant);

        application.setSubmissionRevision(1);
        service.enqueueSubmitted(application);
        application.setSubmissionRevision(2);
        service.enqueueSubmitted(application);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationOutbox>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
        assertThat(captor.getAllValues())
                .flatExtracting(items -> items)
                .extracting(NotificationOutbox::getIdempotencyKey)
                .containsExactly(
                        application.getId() + ":1:APPLICATION_SUBMITTED",
                        application.getId() + ":2:APPLICATION_SUBMITTED"
                );
    }
}
