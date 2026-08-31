package kz.afm.kendala.ai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AiOcrResponse(
        UUID documentId,
        boolean readable,
        List<String> issues,
        String extractedText,
        BigDecimal confidence,
        boolean cropped
) {
}
