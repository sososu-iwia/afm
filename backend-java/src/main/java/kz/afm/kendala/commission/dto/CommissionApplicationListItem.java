package kz.afm.kendala.commission.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record CommissionApplicationListItem(
        UUID id,
        String applicationNumber,
        UUID applicantId,
        String applicantName,
        ApplicationStatus status,
        String region,
        String productionType,
        ActivityType activityType,
        ApplicantCategory applicantCategory,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        Instant decisionAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommissionApplicationListItem from(Application app) {
        return new CommissionApplicationListItem(
                app.getId(),
                app.getApplicationNumber(),
                app.getApplicant().getId(),
                app.getApplicant().getFullName(),
                app.getStatus(),
                app.getRegion(),
                app.getProductionType(),
                app.getActivityType(),
                app.getApplicantCategory(),
                app.getRequestedAmount(),
                app.getApprovedAmount(),
                app.getDecisionAt(),
                app.getCreatedAt(),
                app.getUpdatedAt()
        );
    }
}
