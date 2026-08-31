package kz.afm.kendala.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import kz.afm.kendala.common.exception.ApiException;
import kz.afm.kendala.common.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    public static final String HEADER = "Idempotency-Key";
    private static final String SAFE_KEY = "^[A-Za-z0-9._:-]{1,128}$";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public IdempotencyService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public <T> T execute(
            String key,
            UUID actorId,
            String endpoint,
            Object payload,
            Class<T> responseType,
            Supplier<T> operation
    ) {
        if (key == null || key.isBlank()) {
            return operation.get();
        }
        String normalizedKey = key.strip();
        if (!normalizedKey.matches(SAFE_KEY)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key содержит недопустимые символы или превышает 128 символов"
            );
        }

        String requestHash = hash(payload);
        Instant now = Instant.now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("actorId", actorId)
                .addValue("endpoint", endpoint)
                .addValue("key", normalizedKey)
                .addValue("requestHash", requestHash)
                .addValue("expiresAt", Timestamp.from(now.plusSeconds(ttlSeconds)));

        jdbc.update("""
                DELETE FROM idempotency_records
                WHERE actor_id = :actorId
                  AND endpoint = :endpoint
                  AND idempotency_key = :key
                  AND expires_at <= now()
                """, parameters);

        int inserted = jdbc.update("""
                INSERT INTO idempotency_records(
                    actor_id, endpoint, idempotency_key, request_hash,
                    status, created_at, expires_at
                )
                VALUES (
                    :actorId, :endpoint, :key, :requestHash,
                    'PROCESSING', now(), :expiresAt
                )
                ON CONFLICT (actor_id, endpoint, idempotency_key) DO NOTHING
                """, parameters);

        if (inserted == 0) {
            StoredRecord stored = jdbc.queryForObject("""
                    SELECT request_hash, status, response_json::text
                    FROM idempotency_records
                    WHERE actor_id = :actorId
                      AND endpoint = :endpoint
                      AND idempotency_key = :key
                    FOR UPDATE
                    """, parameters, (rs, rowNum) -> new StoredRecord(
                    rs.getString("request_hash"),
                    rs.getString("status"),
                    rs.getString("response_json")
            ));
            if (stored == null) {
                throw new ConflictException("Idempotency request state is unavailable");
            }
            if (!MessageDigest.isEqual(
                    stored.requestHash().getBytes(StandardCharsets.US_ASCII),
                    requestHash.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw new ConflictException(
                        "Idempotency-Key уже использован с другим запросом",
                        "error.idempotency-conflict"
                );
            }
            if (!"COMPLETED".equals(stored.status()) || stored.responseJson() == null) {
                throw new ConflictException(
                        "Запрос с таким Idempotency-Key ещё выполняется",
                        "error.idempotency-in-progress"
                );
            }
            return read(stored.responseJson(), responseType);
        }

        T result = operation.get();
        String responseJson = write(result);
        parameters.addValue("responseJson", responseJson);
        jdbc.update("""
                UPDATE idempotency_records
                SET status = 'COMPLETED',
                    response_json = CAST(:responseJson AS jsonb)
                WHERE actor_id = :actorId
                  AND endpoint = :endpoint
                  AND idempotency_key = :key
                """, parameters);
        return result;
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron:0 17 * * * *}")
    @Transactional
    public void cleanupExpired() {
        jdbc.update("DELETE FROM idempotency_records WHERE expires_at <= now()", Map.of());
    }

    private String hash(Object payload) {
        byte[] canonical;
        try {
            canonical = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Idempotency payload cannot be serialized", e);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Idempotent response cannot be serialized", e);
        }
    }

    private <T> T read(String value, Class<T> responseType) {
        try {
            return objectMapper.readValue(value, responseType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response is invalid", e);
        }
    }

    private record StoredRecord(String requestHash, String status, String responseJson) {
    }
}
