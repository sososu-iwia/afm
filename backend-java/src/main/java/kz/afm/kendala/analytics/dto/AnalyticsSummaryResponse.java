package kz.afm.kendala.analytics.dto;

import java.math.BigDecimal;
import java.util.Map;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record AnalyticsSummaryResponse(
        long totalApplications,
        Map<ApplicationStatus, Long> applicationsByStatus,
        BigDecimal totalRequestedAmount,
        BigDecimal totalApprovedAmount,
        BigDecimal averageRequestedAmount,
        BigDecimal averageProcessingHours,
        long approvedDecisions,
        long rejectedDecisions,
        long additionalDocumentsRequestedDecisions,
        long completedAiScoringApplications,
        long failedAiTasks
) {
}

