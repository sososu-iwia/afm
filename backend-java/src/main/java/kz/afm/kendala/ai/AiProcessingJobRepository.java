package kz.afm.kendala.ai;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AiProcessingJobRepository extends JpaRepository<AiProcessingJob, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiProcessingJob j where j.id = :id")
    Optional<AiProcessingJob> findLockedById(UUID id);

    Optional<AiProcessingJob> findFirstByOperationTypeAndApplicationIdAndStatusInOrderByCreatedAtDesc(
            AiOperationType operationType, UUID applicationId, Collection<AiTaskStatus> statuses);

    Optional<AiProcessingJob> findFirstByOperationTypeAndApplicationIdOrderByCreatedAtDesc(
            AiOperationType operationType, UUID applicationId);

    Optional<AiProcessingJob> findFirstByOperationTypeAndApplicationIdAndDocumentIdIsNullOrderByCreatedAtDesc(
            AiOperationType operationType, UUID applicationId);

    Optional<AiProcessingJob> findFirstByOperationTypeAndApplicationIdAndSubmissionRevisionAndDocumentIdIsNullOrderByCreatedAtDesc(
            AiOperationType operationType, UUID applicationId, int submissionRevision);

    Optional<AiProcessingJob> findFirstByOperationTypeAndDocumentIdAndStatusInOrderByCreatedAtDesc(
            AiOperationType operationType, UUID documentId, Collection<AiTaskStatus> statuses);

    Optional<AiProcessingJob> findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(
            AiOperationType operationType, UUID documentId);

    List<AiProcessingJob> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<AiProcessingJob> findByApplicationIdAndSubmissionRevisionOrderByCreatedAtAsc(
            UUID applicationId, int submissionRevision);

    List<AiProcessingJob> findByOperationTypeAndApplicationId(AiOperationType operationType, UUID applicationId);

    List<AiProcessingJob> findTop50ByStatusInAndNextAttemptAtLessThanEqualAndAttemptCountLessThanOrderByNextAttemptAtAsc(
            Collection<AiTaskStatus> statuses, Instant now, int maxAttemptCount);

    @Modifying
    @Query("""
            update AiProcessingJob j
            set j.status = kz.afm.kendala.ai.AiTaskStatus.FAILED_RETRYABLE,
                j.errorCode = 'WORKER_INTERRUPTED',
                j.errorMessage = 'AI worker stopped before completion',
                j.nextAttemptAt = CURRENT_TIMESTAMP,
                j.leaseOwner = null,
                j.leaseUntil = null
            where j.status = kz.afm.kendala.ai.AiTaskStatus.PROCESSING
              and (j.leaseUntil is null or j.leaseUntil < :cutoff)
              and j.attemptCount < j.maxAttempts
            """)
    int recoverStaleProcessing(Instant cutoff);

    @Modifying
    @Query("""
            update AiProcessingJob j
            set j.status = kz.afm.kendala.ai.AiTaskStatus.FAILED_TERMINAL,
                j.errorCode = 'WORKER_LEASE_EXPIRED',
                j.errorMessage = 'AI worker lease expired after maximum attempts',
                j.completedAt = CURRENT_TIMESTAMP,
                j.leaseOwner = null,
                j.leaseUntil = null
            where j.status = kz.afm.kendala.ai.AiTaskStatus.PROCESSING
              and (j.leaseUntil is null or j.leaseUntil < :cutoff)
              and j.attemptCount >= j.maxAttempts
            """)
    int failExhaustedStaleProcessing(Instant cutoff);
}
