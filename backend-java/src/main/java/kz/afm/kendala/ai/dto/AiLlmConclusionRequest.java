package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

public record AiLlmConclusionRequest(
        UUID applicationId,
        String region,
        String productionType,
        BigDecimal landArea,
        BigDecimal requestedAmount,
        BigDecimal score,
        String riskCategory,
        BigDecimal recommendedAmount,
        JsonNode topFactors,
        String language
) {
}
