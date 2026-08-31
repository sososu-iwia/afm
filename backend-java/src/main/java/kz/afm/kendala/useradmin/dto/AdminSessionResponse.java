package kz.afm.kendala.useradmin.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminSessionResponse(
        UUID id,
        UUID familyId,
        Instant issuedAt,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant revokedAt,
        String revokeReason,
        String userAgent,
        String ipAddress
) {
}
