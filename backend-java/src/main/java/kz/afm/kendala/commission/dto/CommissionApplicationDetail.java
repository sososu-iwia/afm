package kz.afm.kendala.commission.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.dto.DocumentResponse;
import kz.afm.kendala.application.dto.StatusHistoryResponse;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record CommissionApplicationDetail(
        UUID id,
        String applicationNumber,
        UUID applicantId,
        String applicantName,
        ApplicationStatus status,
        String iinOrBin,
        String region,
        String productionType,
        ActivityType activityType,
        ApplicantCategory applicantCategory,
        BigDecimal landArea,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        Instant decisionAt,
        Instant createdAt,
        Instant updatedAt,
        boolean publicVisible,
        Instant publishedAt,
        List<DocumentResponse> documents,
        List<StatusHistoryResponse> statusHistory,
        List<DecisionResponse> decisions
) {
}
