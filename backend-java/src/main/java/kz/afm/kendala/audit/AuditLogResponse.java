package kz.afm.kendala.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actor,
        /** Имя пользователя вместо идентификатора: журнал читают люди. */
        String actorName,
        String actorRole,
        String action,
        String result,
        String entityType,
        String entityId,
        JsonNode previousValue,
        JsonNode newValue,
        JsonNode metadata,
        String source,
        String failureCode,
        Instant occurredAt,
        String ip,
        String correlationId
) {
}
