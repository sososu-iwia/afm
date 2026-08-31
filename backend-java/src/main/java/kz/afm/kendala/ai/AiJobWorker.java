package kz.afm.kendala.ai;

import java.time.Instant;
import kz.afm.kendala.filestore.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AiJobWorker {
    private static final Logger log = LoggerFactory.getLogger(AiJobWorker.class);
    private final AiJobStateService stateService;
    private final AiGateway gateway;
    private final StorageService storageService;
    private final long retryBaseMillis;
    private final long retryMaxMillis;

    public AiJobWorker(
            AiJobStateService stateService,
            AiGateway gateway,
            StorageService storageService,
            @Value("${app.ai.retry-base-millis:200}") long retryBaseMillis,
            @Value("${app.ai.retry-max-millis:5000}") long retryMaxMillis
    ) {
        this.stateService = stateService;
        this.gateway = gateway;
        this.storageService = storageService;
        this.retryBaseMillis = retryBaseMillis;
        this.retryMaxMillis = retryMaxMillis;
    }

    @Async("aiTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(AiJobQueuedEvent event) {
        while (true) {
            AiWorkItem item = stateService.start(event.jobId());
            if (item == null) return;
            int attempt = item.attemptCount() + 1;
            stateService.recordAttempt(item.jobId());
            try {
                if (item.operationType() == AiOperationType.SCORING) {
                    stateService.completeScore(item.jobId(), gateway.score(item.scoreRequest()));
                } else if (item.operationType() == AiOperationType.OCR) {
                    byte[] content = storageService.load(item.storageKey());
                    stateService.completeOcr(item.jobId(), gateway.ocr(
                            item.documentId(), item.fileName(), item.contentType(), content));
                } else if (item.operationType() == AiOperationType.DUPLICATE_CHECK) {
                    stateService.completeDuplicateCheck(
                            item.jobId(), gateway.duplicateCheck(item.duplicateCheckRequest()));
                } else if (item.operationType() == AiOperationType.LLM_CONCLUSION) {
                    stateService.completeLlmConclusion(
                            item.jobId(), gateway.llmConclusion(item.llmConclusionRequest()));
                }
                return;
            } catch (AiGatewayException exception) {
                boolean last = attempt >= item.maxAttempts() || !exception.isRetryable();
                if (last) {
                    if (item.operationType() == AiOperationType.LLM_CONCLUSION) {
                        stateService.failOptional(item.jobId(), exception.getCode(), exception.getMessage());
                    } else {
                        stateService.fail(item.jobId(), exception.getCode(), exception.getMessage());
                    }
                    return;
                }
                long delay = backoff(attempt);
                stateService.retryableFailure(
                        item.jobId(),
                        exception.getCode(),
                        exception.getMessage(),
                        Instant.now().plusMillis(delay)
                );
                pause(delay);
            } catch (Exception exception) {
                log.error("Unexpected AI job failure jobId={}", item.jobId(), exception);
                if (item.operationType() == AiOperationType.LLM_CONCLUSION) {
                    stateService.failOptional(item.jobId(), "AI_PROCESSING_ERROR", "Optional AI processing failed");
                } else {
                    stateService.fail(item.jobId(), "AI_PROCESSING_ERROR", "AI processing failed");
                }
                return;
            }
        }
    }

    private long backoff(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long multiplier = 1L << exponent;
        if (retryBaseMillis > Long.MAX_VALUE / multiplier) {
            return retryMaxMillis;
        }
        return Math.min(retryMaxMillis, retryBaseMillis * multiplier);
    }

    private void pause(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
