package kz.afm.kendala.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import kz.afm.kendala.auth.service.SmsDeliveryResult;
import kz.afm.kendala.auth.service.SmsSender;
import kz.afm.kendala.observability.DomainMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class NotificationWorkerTest {
    private final NotificationDeliveryStateService stateService =
            mock(NotificationDeliveryStateService.class);
    private final SmsSender smsSender = mock(SmsSender.class);
    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final NotificationWorker worker =
            new NotificationWorker(
                    stateService,
                    smsSender,
                    mailSender,
                    "no-reply@example.org",
                    mock(DomainMetrics.class)
            );

    @Test
    void sendsSmsThroughConfiguredProviderAndMarksSent() {
        UUID id = UUID.randomUUID();
        when(stateService.start(id)).thenReturn(new NotificationWorkItem(
                id, NotificationChannel.SMS, "+77000000000", null, "Status changed", 1, 3));
        when(smsSender.send("+77000000000", "Status changed"))
                .thenReturn(SmsDeliveryResult.sent("sms-1"));

        worker.processPending(id);

        verify(smsSender).send("+77000000000", "Status changed");
        verify(stateService).sent(id, "sms-1");
    }

    @Test
    void sendsEmailThroughConfiguredProviderAndMarksSent() {
        UUID id = UUID.randomUUID();
        when(stateService.start(id)).thenReturn(new NotificationWorkItem(
                id, NotificationChannel.EMAIL, "applicant@example.org",
                "Application status", "Status changed", 1, 3));

        worker.processPending(id);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        verify(stateService).sent(id);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getTo())
                .containsExactly("applicant@example.org");
    }

    @Test
    void localDevSmsDeliveryIsSuccessfulButKeepsItsProviderIdentity() {
        UUID id = UUID.randomUUID();
        when(stateService.start(id)).thenReturn(new NotificationWorkItem(
                id, NotificationChannel.SMS, "+77000000000", null, "Status changed", 1, 3));
        when(smsSender.send("+77000000000", "Status changed"))
                .thenReturn(SmsDeliveryResult.deliveredLocal("local-1"));

        worker.processPending(id);

        verify(stateService).sent(id, "local-1");
    }
}
