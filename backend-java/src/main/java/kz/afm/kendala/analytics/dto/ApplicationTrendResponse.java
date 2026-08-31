package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApplicationTrendResponse(
        LocalDate date,
        long applicationCount,
        BigDecimal requestedAmount
) {
}

