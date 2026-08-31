package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProcessingTimeResponse(
        LocalDate decisionDate,
        long completedApplications,
        BigDecimal averageProcessingHours
) {
}

