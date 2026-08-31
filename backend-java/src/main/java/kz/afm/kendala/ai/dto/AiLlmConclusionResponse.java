package kz.afm.kendala.ai.dto;

import java.util.UUID;

public record AiLlmConclusionResponse(
        UUID applicationId,
        String text
) {
}
