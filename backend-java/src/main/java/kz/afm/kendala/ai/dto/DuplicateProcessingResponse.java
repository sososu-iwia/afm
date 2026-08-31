package kz.afm.kendala.ai.dto;

import java.util.List;

public record DuplicateProcessingResponse(
        String status,
        Boolean hasDuplicates,
        String duplicateType,
        String matchedApplicationId,
        List<String> flags,
        List<String> anomalies
) {
}
