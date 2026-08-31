package kz.afm.kendala.publicregistry;

import java.math.BigDecimal;
import java.time.Instant;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;

public record PublicApprovedApplicationResponse(
        String applicationNumber,
        String region,
        ActivityType activityType,
        String productionType,
        BigDecimal approvedAmount,
        Instant decisionDate,
        ApplicantCategory applicantCategory,
        String scoringCategory
) {
}

