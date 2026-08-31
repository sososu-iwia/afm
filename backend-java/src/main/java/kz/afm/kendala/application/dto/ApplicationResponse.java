package kz.afm.kendala.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record ApplicationResponse(
        UUID id,
        String applicationNumber,
        UUID applicantId,
        ApplicationStatus status,
        String iinOrBin,
        String region,
        String productionType,
        ActivityType activityType,
        ApplicantCategory applicantCategory,
        BigDecimal landArea,
        BigDecimal requestedAmount,
        Instant createdAt,
        Instant updatedAt,
        List<DocumentResponse> documents,
        int submissionRevision
) {
    public ApplicationResponse(
            UUID id,
            String applicationNumber,
            UUID applicantId,
            ApplicationStatus status,
            String iinOrBin,
            String region,
            String productionType,
            ActivityType activityType,
            ApplicantCategory applicantCategory,
            BigDecimal landArea,
            BigDecimal requestedAmount,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, applicationNumber, applicantId, status, iinOrBin, region, productionType,
                activityType, applicantCategory, landArea, requestedAmount, createdAt, updatedAt, List.of(), 0);
    }

    public ApplicationResponse(
            UUID id, String applicationNumber, UUID applicantId, ApplicationStatus status,
            String iinOrBin, String region, String productionType, ActivityType activityType,
            ApplicantCategory applicantCategory, BigDecimal landArea, BigDecimal requestedAmount,
            Instant createdAt, Instant updatedAt, List<DocumentResponse> documents
    ) {
        this(id, applicationNumber, applicantId, status, iinOrBin, region, productionType,
                activityType, applicantCategory, landArea, requestedAmount, createdAt, updatedAt, documents, 0);
    }
}
