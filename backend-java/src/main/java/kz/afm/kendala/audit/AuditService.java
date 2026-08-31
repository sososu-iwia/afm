package kz.afm.kendala.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.repository.UserRepository;
import java.util.UUID;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.PaginationPolicy;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "iinorbin",
            "phone",
            "email",
            "destination",
            "token",
            "accesstoken",
            "refreshtoken",
            "password",
            "otp",
            "code",
            "codehash",
            "storagekey",
            "extractedtext"
    );
    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public AuditService(
            AuditLogRepository repository,
            ObjectMapper objectMapper,
            UserRepository userRepository
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String actor, String role, String action, String entityType, String entityId,
            Object previousValue, Object newValue, String ip, String correlationId
    ) {
        save(actor, role, action, entityType, entityId,
                previousValue, newValue, ip, correlationId, "SUCCESS", inferSource(role), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String actor, String role, String action, String entityType, String entityId,
            Object previousValue, Object newValue, String ip, String correlationId,
            String result, String source, String failureCode
    ) {
        save(actor, role, action, entityType, entityId,
                previousValue, newValue, ip, correlationId, result, source, failureCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDomain(
            User actor, String action, String entityType, String entityId,
            Object previousValue, Object newValue
    ) {
        String ip = "system";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            ip = attributes.getRequest().getRemoteAddr();
        }
        save(actor.getId().toString(), actor.getRole().name(), action, entityType, entityId,
                previousValue, newValue, ip, MDC.get("correlationId"), "SUCCESS", sourceForRole(actor.getRole().name()), null);
    }

    private void save(
            String actor, String role, String action, String entityType, String entityId,
            Object previousValue, Object newValue, String ip, String correlationId,
            String result, String source, String failureCode
    ) {
        AuditLog log = new AuditLog();
        log.setActor(safe(actor, "anonymous", 64));
        log.setActorRole(safe(role, "ANONYMOUS", 64));
        log.setAction(safe(action, "UNKNOWN", 128));
        log.setResult(safe(result, "SUCCESS", 32));
        log.setEntityType(safe(entityType, "UNKNOWN", 64));
        log.setEntityId(safe(entityId, "-", 128));
        log.setPreviousValue(json(previousValue));
        log.setNewValue(json(newValue));
        log.setMetadata(json(newValue));
        log.setSource(safe(source, "SYSTEM", 32));
        log.setFailureCode(safeNullable(failureCode, 64));
        log.setIp(safe(ip, "unknown", 64));
        log.setCorrelationId(safe(correlationId, "unknown", 100));
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(
            String actor, String action, String entityType, String entityId,
            Instant from, Instant to, String correlationId, int page, int size
    ) {
        var spec = (org.springframework.data.jpa.domain.Specification<AuditLog>) (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(actor)) {
                predicates.add(cb.equal(root.get("actor"), actor));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (StringUtils.hasText(entityType)) {
                predicates.add(cb.equal(root.get("entityType"), entityType));
            }
            if (StringUtils.hasText(entityId)) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), to));
            }
            if (StringUtils.hasText(correlationId)) {
                predicates.add(cb.equal(root.get("correlationId"), correlationId));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = repository.findAll(spec, PageRequest.of(
                PaginationPolicy.zeroBased(page), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "occurredAt")));
        Map<String, String> names = resolveActorNames(result.getContent());
        return new PageResponse<>(
                result.getContent().stream().map(log -> response(log, names)).toList(),
                result.getNumber() + 1, result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Actor хранится строкой: это либо UUID пользователя, либо "anonymousUser". */
    private Map<String, String> resolveActorNames(java.util.List<AuditLog> logs) {
        var ids = logs.stream()
                .map(AuditLog::getActor)
                .filter(StringUtils::hasText)
                .map(this::asUuid)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new java.util.HashMap<>();
        userRepository.findAllById(ids).forEach(user ->
                names.put(user.getId().toString(), user.getFullName()));
        return names;
    }

    private UUID asUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AuditLogResponse response(AuditLog log, Map<String, String> actorNames) {
        return new AuditLogResponse(
                log.getId(), log.getActor(),
                actorNames.get(log.getActor()),
                log.getActorRole(), log.getAction(),
                log.getResult(),
                log.getEntityType(), log.getEntityId(),
                read(log.getPreviousValue()), read(log.getNewValue()),
                read(log.getMetadata()), log.getSource(), log.getFailureCode(),
                log.getOccurredAt(), log.getIp(), log.getCorrelationId());
    }

    private String json(Object value) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            redact(tree);
            return objectMapper.writeValueAsString(tree);
        } catch (JsonProcessingException e) {
            return "null";
        }
    }

    private void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.properties().forEach(entry -> {
                if (SENSITIVE_FIELDS.contains(entry.getKey().toLowerCase())) {
                    object.put(entry.getKey(), "[REDACTED]");
                } else {
                    redact(entry.getValue());
                }
            });
        } else if (node instanceof ArrayNode array) {
            array.forEach(this::redact);
        }
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            return objectMapper.nullNode();
        }
    }

    private String safe(String value, String fallback, int max) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.substring(0, Math.min(result.length(), max));
    }

    private String safeNullable(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), max));
    }

    private String inferSource(String role) {
        if ("SYSTEM".equals(role)) {
            return "SYSTEM";
        }
        return sourceForRole(role);
    }

    private String sourceForRole(String role) {
        if ("ADMIN".equals(role)) {
            return "ADMIN";
        }
        if ("ANONYMOUS".equals(role)) {
            return "USER";
        }
        return "USER";
    }
}
