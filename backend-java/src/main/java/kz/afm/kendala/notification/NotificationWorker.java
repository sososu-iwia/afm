package kz.afm.kendala.notification;

import java.time.Duration;
import java.time.Instant;
import kz.afm.kendala.auth.service.SmsDeliveryResult;
import kz.afm.kendala.auth.service.SmsDeliveryStatus;
import kz.afm.kendala.auth.service.SmsSender;
import kz.afm.kendala.common.exception.ServiceUnavailableException;
import kz.afm.kendala.observability.DomainMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationWorker {
    private final NotificationDeliveryStateService stateService;
    private final SmsSender smsSender;
    private final JavaMailSender mailSender;
    private final String emailFrom;
    private final DomainMetrics metrics;

    public NotificationWorker(
            NotificationDeliveryStateService stateService,
            SmsSender smsSender,
            JavaMailSender mailSender,
            @Value("${app.notifications.email-from:}") String emailFrom,
            DomainMetrics metrics
    ) {
        this.stateService = stateService;
        this.smsSender = smsSender;
        this.mailSender = mailSender;
        this.emailFrom = emailFrom;
        this.metrics = metrics;
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationQueuedEvent event) {
        deliver(event.notificationId());
    }

    @Async("aiTaskExecutor")
    public void processPending(java.util.UUID id) {
        deliver(id);
    }

    private void deliver(java.util.UUID id) {
        NotificationWorkItem item = stateService.start(id);
        if (item == null) return;
        Instant startedAt = Instant.now();
        String result = "failure";
        try {
            if (item.channel() == NotificationChannel.SMS) {
                SmsDeliveryResult delivery = smsSender.send(item.destination(), item.body());
                if (delivery != null && delivery.status() == SmsDeliveryStatus.NOT_CONFIGURED) {
                    stateService.notConfigured(id, delivery.errorCode());
                    result = "not_configured";
                    return;
                }
                if (delivery != null
                        && delivery.status() != SmsDeliveryStatus.SENT
                        && delivery.status() != SmsDeliveryStatus.DELIVERED_LOCAL) {
                    stateService.failed(id, delivery.errorCode());
                    return;
                }
                stateService.sent(id, delivery == null ? null : delivery.providerMessageId());
                result = "success";
                return;
            } else {
                if (!StringUtils.hasText(emailFrom)) {
                    throw new ServiceUnavailableException(
                            "EMAIL_NOT_CONFIGURED",
                            "Email notifications are not configured"
                    );
                }
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(emailFrom);
                message.setTo(item.destination());
                message.setSubject(item.subject());
                message.setText(item.body());
                mailSender.send(message);
            }
            stateService.sent(id);
            result = "success";
        } catch (ServiceUnavailableException exception) {
            if (exception.getCode() != null && exception.getCode().endsWith("_NOT_CONFIGURED")) {
                stateService.notConfigured(id, exception.getCode());
                result = "not_configured";
            } else {
                stateService.failed(id, exception.getCode());
            }
        } catch (Exception exception) {
            stateService.failed(id, "Provider unavailable");
        } finally {
            metrics.externalRequest(
                    item.channel().name().toLowerCase(),
                    "notification",
                    result,
                    Duration.between(startedAt, Instant.now())
            );
        }
    }
}
