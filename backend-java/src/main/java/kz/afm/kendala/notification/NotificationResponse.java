package kz.afm.kendala.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Уведомление в личном кабинете.
 *
 * Адрес доставки наружу не отдаётся: пользователь и так знает свой номер,
 * а лишний раз показывать контакт в API незачем.
 */
public record NotificationResponse(
        UUID id,
        String channel,
        String eventType,
        String subject,
        String body,
        String status,
        Instant createdAt,
        Instant sentAt
) {
    public static NotificationResponse from(NotificationOutbox outbox) {
        return new NotificationResponse(
                outbox.getId(),
                outbox.getChannel() == null ? null : outbox.getChannel().name(),
                outbox.getEventType(),
                outbox.getSubject(),
                outbox.getBody(),
                outbox.getStatus() == null ? null : outbox.getStatus().name(),
                outbox.getCreatedAt(),
                outbox.getSentAt()
        );
    }
}
