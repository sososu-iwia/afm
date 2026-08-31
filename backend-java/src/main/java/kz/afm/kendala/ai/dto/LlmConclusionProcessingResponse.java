package kz.afm.kendala.ai.dto;

public record LlmConclusionProcessingResponse(
        String status,
        String text,
        String errorCode,
        String errorMessage
) {
}
