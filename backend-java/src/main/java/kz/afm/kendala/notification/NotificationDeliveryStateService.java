package kz.afm.kendala.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.observability.DomainMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryStateService {
    private final NotificationOutboxRepository repository;
    private final DomainMetrics metrics;
    private final long baseDelaySeconds;
    private final long maxDelaySeconds;

    public NotificationDeliveryStateService(
            NotificationOutboxRepository repository,
            DomainMetrics metrics,
            @Value("${app.notifications.retry-base-seconds:5}") long baseDelaySeconds,
            @Value("${app.notifications.retry-max-seconds:300}") long maxDelaySeconds
    ) {
        this.repository = repository;
        this.metrics = metrics;
        this.baseDelaySeconds = baseDelaySeconds;
        this.maxDelaySeconds = maxDelaySeconds;
    }

    @Transactional
    public NotificationWorkItem start(UUID id) {
        NotificationOutbox notification = repository.findLockedById(id).orElse(null);
        if (notification == null
                || (notification.getStatus() != NotificationStatus.PENDING
                && notification.getStatus() != NotificationStatus.FAILED)
                || notification.getAttemptCount() >= notification.getMaxAttempts()
                || notification.getNextAttemptAt().isAfter(Instant.now())) {
            return null;
        }
        notification.setStatus(NotificationStatus.SENDING);
        notification.setAttemptCount(notification.getAttemptCount() + 1);
        return new NotificationWorkItem(
                notification.getId(), notification.getChannel(), notification.getDestination(),
                notification.getSubject(), notification.getBody(),
                notification.getAttemptCount(), notification.getMaxAttempts());
    }

    @Transactional
    public void sent(UUID id) {
        sent(id, null);
    }

    @Transactional
    public void sent(UUID id, String providerMessageId) {
        repository.findLockedById(id).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.SENT);
            notification.setLastError(null);
            notification.setErrorCode(null);
            notification.setProviderMessageId(providerMessageId);
            notification.setSentAt(Instant.now());
            metrics.increment("notification.sent");
        });
    }

    @Transactional
    public void failed(UUID id, String error) {
        repository.findLockedById(id).ifPresent(notification -> {
            boolean exhausted = notification.getAttemptCount() >= notification.getMaxAttempts();
            notification.setStatus(exhausted ? NotificationStatus.DEAD : NotificationStatus.FAILED);
            String safe = error == null ? "Notification delivery failed" : error;
            notification.setLastError(safe.substring(0, Math.min(safe.length(), 1000)));
            notification.setErrorCode(error == null ? "NOTIFICATION_FAILED" : error.substring(0, Math.min(error.length(), 64)));
            if (!exhausted) {
                notification.setNextAttemptAt(Instant.now().plusSeconds(backoff(
                        notification.getAttemptCount()
                )));
            }
            metrics.increment("notification.failed");
        });
    }

    @Transactional
    public void notConfigured(UUID id, String error) {
        repository.findLockedById(id).ifPresent(notification -> {
            notification.setStatus(NotificationStatus.NOT_CONFIGURED);
            String safe = error == null ? "Notification channel is not configured" : error;
            notification.setLastError(safe.substring(0, Math.min(safe.length(), 1000)));
            notification.setErrorCode(error == null ? "NOT_CONFIGURED" : error.substring(0, Math.min(error.length(), 64)));
            notification.setNextAttemptAt(Instant.now());
            metrics.increment("notification.not_configured");
        });
    }

    @Transactional
    public List<UUID> recoverAndFindPending() {
        Instant now = Instant.now();
        Instant cutoff = now.minusSeconds(120);
        repository.recoverRetryableStale(cutoff);
        repository.recoverExhaustedStale(cutoff);
        return repository.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                        List.of(NotificationStatus.PENDING, NotificationStatus.FAILED), now)
                .stream()
                .filter(item -> item.getAttemptCount() < item.getMaxAttempts())
                .map(NotificationOutbox::getId)
                .toList();
    }

    private long backoff(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long multiplier = 1L << exponent;
        if (baseDelaySeconds > Long.MAX_VALUE / multiplier) {
            return maxDelaySeconds;
        }
        return Math.min(maxDelaySeconds, baseDelaySeconds * multiplier);
    }
}
