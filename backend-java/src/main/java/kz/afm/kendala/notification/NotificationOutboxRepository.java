package kz.afm.kendala.notification;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationOutbox n where n.id = :id")
    Optional<NotificationOutbox> findLockedById(UUID id);

    boolean existsByIdempotencyKeyAndChannel(String idempotencyKey, NotificationChannel channel);

    List<NotificationOutbox> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Collection<NotificationStatus> statuses, Instant now);

    @Modifying
    @Query("""
            update NotificationOutbox n
            set n.status = kz.afm.kendala.notification.NotificationStatus.FAILED,
                n.lastError = 'Delivery interrupted before completion',
                n.errorCode = 'DELIVERY_INTERRUPTED',
                n.nextAttemptAt = CURRENT_TIMESTAMP
            where n.status = kz.afm.kendala.notification.NotificationStatus.SENDING
              and n.updatedAt < :cutoff
              and n.attemptCount < n.maxAttempts
            """)
    int recoverRetryableStale(Instant cutoff);

    @Modifying
    @Query("""
            update NotificationOutbox n
            set n.status = kz.afm.kendala.notification.NotificationStatus.DEAD,
                n.lastError = 'Delivery attempts exhausted',
                n.errorCode = 'DELIVERY_ATTEMPTS_EXHAUSTED'
            where n.status = kz.afm.kendala.notification.NotificationStatus.SENDING
              and n.updatedAt < :cutoff
              and n.attemptCount >= n.maxAttempts
            """)
    int recoverExhaustedStale(Instant cutoff);

    /** Уведомления пользователя для личного кабинета, новые сверху. */
    List<NotificationOutbox> findTop100ByUserIdOrderByCreatedAtDesc(UUID userId);
}
