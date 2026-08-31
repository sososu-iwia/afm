package kz.afm.kendala.notification;

import java.util.ArrayList;
import java.util.List;
import kz.afm.kendala.application.dto.CompletenessResult;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.enums.ApplicationStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

@Service
public class NotificationService {
    private final NotificationOutboxRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxAttempts;

    public NotificationService(
            NotificationOutboxRepository repository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.notifications.max-attempts:3}") int maxAttempts
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void enqueueSubmitted(Application application) {
        String body = "Заявка " + application.getApplicationNumber()
                + ": принята в обработку. Статус " + application.getStatus();
        enqueue(application, "APPLICATION_SUBMITTED",
                "Заявка принята в обработку " + application.getApplicationNumber(), body);
    }

    @Transactional
    public void enqueueDocumentsMissing(Application application, CompletenessResult completeness) {
        String missing = String.join(", ", completeness.missingDocuments());
        String body = "Заявка " + application.getApplicationNumber()
                + ": требуются документы" + (missing.isBlank() ? "" : " " + missing);
        enqueue(application, "DOCUMENTS_MISSING",
                "Требуются документы " + application.getApplicationNumber(), body);
    }

    @Transactional
    public void enqueueReadyForReview(Application application) {
        String body = "Заявка " + application.getApplicationNumber()
                + ": обработка завершена, заявка готова к рассмотрению комиссией";
        enqueue(application, "READY_FOR_REVIEW",
                "Заявка готова к рассмотрению " + application.getApplicationNumber(), body);
    }

    @Transactional
    public void enqueueProcessingPartiallyFailed(Application application, String reason) {
        String body = "Заявка " + application.getApplicationNumber()
                + ": часть AI-обработки недоступна"
                + (StringUtils.hasText(reason) ? ". Детали: " + reason.strip() : "");
        enqueue(application, "AI_PROCESSING_PARTIALLY_FAILED",
                "AI-обработка частично недоступна " + application.getApplicationNumber(), body);
    }

    @Transactional
    public void enqueueDecision(Application application, ApplicationStatus status, String reason) {
        String body = "Заявка " + application.getApplicationNumber() + ": статус " + status
                + (reason == null || reason.isBlank() ? "" : ". Комментарий: " + reason.strip());
        enqueue(application, eventType(status),
                "Изменение статуса заявки " + application.getApplicationNumber(), body);
    }

    private void enqueue(Application application, String eventType, String subject, String body) {
        List<NotificationOutbox> queued = new ArrayList<>();
        addIfNew(queued, application, eventType, NotificationChannel.SMS,
                application.getApplicant().getPhone(), null, body);
        if (StringUtils.hasText(application.getApplicant().getEmail())) {
            addIfNew(queued, application, eventType, NotificationChannel.EMAIL,
                    application.getApplicant().getEmail(), subject, body);
        }
        if (queued.isEmpty()) {
            return;
        }
        repository.saveAll(queued);
        queued.forEach(item -> eventPublisher.publishEvent(new NotificationQueuedEvent(item.getId())));
    }

    private void addIfNew(
            List<NotificationOutbox> queued,
            Application application,
            String eventType,
            NotificationChannel channel,
            String destination,
            String subject,
            String body
    ) {
        if (!StringUtils.hasText(destination)) {
            return;
        }
        String key = key(application, eventType);
        if (repository.existsByIdempotencyKeyAndChannel(key, channel)) {
            return;
        }
        queued.add(create(application, channel, destination, subject, body, eventType, key));
    }

    private NotificationOutbox create(
            Application application,
            NotificationChannel channel,
            String destination,
            String subject,
            String body,
            String eventType,
            String idempotencyKey
    ) {
        NotificationOutbox notification = new NotificationOutbox();
        notification.setUser(application.getApplicant());
        notification.setApplication(application);
        notification.setChannel(channel);
        notification.setDestination(destination);
        notification.setSubject(subject);
        notification.setBody(body.substring(0, Math.min(body.length(), 2000)));
        notification.setEventType(eventType);
        notification.setTemplateKey(eventType);
        notification.setSafePayload("{\"applicationNumber\":\"" + escapeJson(application.getApplicationNumber())
                + "\",\"eventType\":\"" + escapeJson(eventType)
                + "\",\"submissionRevision\":" + application.getSubmissionRevision() + "}");
        notification.setLocale("ru");
        notification.setIdempotencyKey(idempotencyKey);
        notification.setCorrelationId(MDC.get("correlationId"));
        notification.setStatus(NotificationStatus.PENDING);
        notification.setMaxAttempts(maxAttempts);
        return notification;
    }

    private String key(Application application, String eventType) {
        return application.getId() + ":" + application.getSubmissionRevision() + ":" + eventType;
    }

    private String eventType(ApplicationStatus status) {
        return switch (status) {
            case APPROVED -> "APPLICATION_APPROVED";
            case REJECTED -> "APPLICATION_REJECTED";
            case ADDITIONAL_DOCUMENTS_REQUESTED -> "DOCUMENTS_REQUESTED";
            case IN_REVIEW -> "READY_FOR_REVIEW";
            case SUBMITTED -> "APPLICATION_SUBMITTED";
            case WITHDRAWN -> "APPLICATION_WITHDRAWN";
            case DRAFT -> "APPLICATION_DRAFT";
        };
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
