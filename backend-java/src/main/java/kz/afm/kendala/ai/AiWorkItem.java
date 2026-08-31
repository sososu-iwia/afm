package kz.afm.kendala.ai;

import java.util.UUID;
import kz.afm.kendala.ai.dto.AiDuplicateCheckRequest;
import kz.afm.kendala.ai.dto.AiLlmConclusionRequest;
import kz.afm.kendala.ai.dto.AiScoreRequest;

public record AiWorkItem(
        UUID jobId,
        AiOperationType operationType,
        int attemptCount,
        int maxAttempts,
        AiScoreRequest scoreRequest,
        AiDuplicateCheckRequest duplicateCheckRequest,
        AiLlmConclusionRequest llmConclusionRequest,
        UUID documentId,
        String storageKey,
        String fileName,
        String contentType
) {
}
