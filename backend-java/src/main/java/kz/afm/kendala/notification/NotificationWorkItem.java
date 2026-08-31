package kz.afm.kendala.notification;

import java.util.UUID;

public record NotificationWorkItem(
        UUID id,
        NotificationChannel channel,
        String destination,
        String subject,
        String body,
        int attemptCount,
        int maxAttempts
) {
}
