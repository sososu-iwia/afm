package kz.afm.kendala.commission.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Decision;
import kz.afm.kendala.application.enums.DecisionType;
import kz.afm.kendala.application.enums.UserRole;

public record DecisionResponse(
        UUID id,
        UUID applicationId,
        UUID decidedById,
        String decidedByName,
        DecisionType decisionType,
        String reason,
        UserRole actorRole,
        String comment,
        BigDecimal requestedAmount,
        BigDecimal approvedAmount,
        String correlationId,
        String protocolReference,
        Long version,
        Instant createdAt,
        Integer submissionRevision,
        UUID scoringJobId,
        BigDecimal aiScore,
        String aiRiskCategory,
        String aiModelName,
        String aiModelVersion
) {
    public static DecisionResponse from(Decision d) {
        return new DecisionResponse(
                d.getId(),
                d.getApplication().getId(),
                d.getDecidedBy().getId(),
                d.getDecidedBy().getFullName(),
                d.getDecisionType(),
                d.getReason(),
                d.getActorRole(),
                d.getComment(),
                d.getRequestedAmount(),
                d.getApprovedAmount(),
                d.getCorrelationId(),
                d.getProtocolReference(),
                d.getVersion(),
                d.getCreatedAt(),
                d.getSubmissionRevision(),
                d.getScoringJob() == null ? null : d.getScoringJob().getId(),
                d.getAiScore(),
                d.getAiRiskCategory(),
                d.getAiModelName(),
                d.getAiModelVersion()
        );
    }
}
