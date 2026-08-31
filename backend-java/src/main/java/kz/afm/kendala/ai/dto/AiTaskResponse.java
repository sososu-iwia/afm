package kz.afm.kendala.ai.dto;

import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.ai.AiProcessingJob;
import kz.afm.kendala.ai.AiTaskStatus;

public record AiTaskResponse(
        UUID jobId,
        AiTaskStatus status,
        int attemptCount,
        int maxAttempts,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {
    public static AiTaskResponse from(AiProcessingJob job) {
        return new AiTaskResponse(
                job.getId(), job.getStatus(), job.getAttemptCount(), job.getMaxAttempts(),
                job.getErrorCode(), job.getErrorMessage(), job.getCreatedAt(), job.getCompletedAt());
    }
}
