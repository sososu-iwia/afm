package kz.afm.kendala.ai.dto;

import java.util.UUID;
import kz.afm.kendala.application.dto.CompletenessResult;

public record ApplicationProcessingResponse(
        UUID applicationId,
        String overallStatus,
        CompletenessResult completeness,
        OcrProcessingResponse ocr,
        DuplicateProcessingResponse duplicateCheck,
        ScoringProcessingResponse scoring,
        LlmConclusionProcessingResponse llmConclusion,
        int submissionRevision
) {
    public ApplicationProcessingResponse(
            UUID applicationId, String overallStatus, CompletenessResult completeness,
            OcrProcessingResponse ocr, DuplicateProcessingResponse duplicateCheck,
            ScoringProcessingResponse scoring, LlmConclusionProcessingResponse llmConclusion
    ) {
        this(applicationId, overallStatus, completeness, ocr, duplicateCheck, scoring, llmConclusion, 0);
    }
}
