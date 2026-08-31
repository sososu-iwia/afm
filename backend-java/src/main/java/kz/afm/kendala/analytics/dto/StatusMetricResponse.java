package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record StatusMetricResponse(
        ApplicationStatus status,
        long applicationCount,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount
) {
}

