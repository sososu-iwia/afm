package kz.afm.kendala.application.dto;

import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.enums.ApplicationStatus;

public record StatusHistoryResponse(
        UUID id,
        ApplicationStatus previousStatus,
        ApplicationStatus newStatus,
        UUID changedBy,
        String reason,
        String comment,
        Instant createdAt
) {
}
