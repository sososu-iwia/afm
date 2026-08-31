package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record ScoreResultResponse(
        BigDecimal score,
        String riskCategory,
        BigDecimal recommendedAmount,
        JsonNode topFactors,
        String llmSummary,
        String modelName,
        String modelVersion,
        JsonNode modelMetadata,
        Instant checkedAt
) {
    public ScoreResultResponse(
            BigDecimal score,
            String riskCategory,
            BigDecimal recommendedAmount,
            JsonNode topFactors,
            String llmSummary,
            Instant checkedAt
    ) {
        this(score, riskCategory, recommendedAmount, topFactors, llmSummary, null, null, null, checkedAt);
    }
}
