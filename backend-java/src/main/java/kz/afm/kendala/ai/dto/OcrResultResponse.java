package kz.afm.kendala.ai.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OcrResultResponse(
        boolean readable,
        BigDecimal confidence,
        List<String> issues,
        String extractedText,
        Instant checkedAt
) {
}
