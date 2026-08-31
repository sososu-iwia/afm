package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;

public record DecisionRateResponse(
        long approvedApplications,
        long rejectedApplications,
        long finalApplications,
        BigDecimal approvalRatePercent,
        BigDecimal rejectionRatePercent
) {
}
