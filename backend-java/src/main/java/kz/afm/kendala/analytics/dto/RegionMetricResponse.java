package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;

public record RegionMetricResponse(
        String region,
        long applicationCount,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount
) {
}

