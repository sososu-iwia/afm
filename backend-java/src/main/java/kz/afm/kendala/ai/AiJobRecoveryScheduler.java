package kz.afm.kendala.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
        name = "app.ai.recovery-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AiJobRecoveryScheduler {

    private final AiProcessingJobRepository repository;
    private final AiJobWorker worker;

    public AiJobRecoveryScheduler(AiProcessingJobRepository repository, AiJobWorker worker) {
        this.repository = repository;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${app.ai.recovery-delay-ms:30000}")
    @Transactional
    public void recover() {
        Instant now = Instant.now();
        repository.recoverStaleProcessing(now.minusSeconds(120));
        repository.failExhaustedStaleProcessing(now.minusSeconds(120));
        List<UUID> due = repository
                .findTop50ByStatusInAndNextAttemptAtLessThanEqualAndAttemptCountLessThanOrderByNextAttemptAtAsc(
                        List.of(AiTaskStatus.PENDING, AiTaskStatus.FAILED_RETRYABLE, AiTaskStatus.FAILED),
                        now,
                        Integer.MAX_VALUE
                )
                .stream()
                .filter(job -> job.getAttemptCount() < job.getMaxAttempts())
                .map(AiProcessingJob::getId)
                .toList();
        due.forEach(id -> worker.process(new AiJobQueuedEvent(id)));
    }
}
