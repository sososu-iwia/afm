package kz.afm.kendala.ai.dto;

import java.util.List;
import java.util.UUID;

public record AiDuplicateCheckResponse(
        UUID applicationId,
        boolean hasDuplicates,
        String duplicateType,
        String matchedApplicationId,
        List<String> flags,
        List<String> anomalies
) {
}
