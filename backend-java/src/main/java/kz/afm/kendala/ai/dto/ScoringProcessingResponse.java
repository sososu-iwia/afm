package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record ScoringProcessingResponse(
        String status,
        BigDecimal score,
        String riskCategory,
        String modelName,
        String modelVersion,
        /** Рекомендованный моделью лимит — комиссия должна видеть его до решения. */
        BigDecimal recommendedAmount,
        JsonNode topFactors
) {
}
