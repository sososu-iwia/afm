package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.UUID;

public record AiScoreResponse(
        UUID applicationId,
        BigDecimal score,
        String riskCategory,
        BigDecimal recommendedAmount,
        JsonNode topFactors,
        String llmSummary,
        String modelName,
        String modelVersion,
        JsonNode modelMetadata
) {
    public AiScoreResponse(
            UUID applicationId,
            BigDecimal score,
            String riskCategory,
            BigDecimal recommendedAmount,
            JsonNode topFactors,
            String llmSummary
    ) {
        this(applicationId, score, riskCategory, recommendedAmount, topFactors, llmSummary, null, null, null);
    }
}
