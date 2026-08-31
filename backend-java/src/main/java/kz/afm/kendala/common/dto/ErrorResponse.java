package kz.afm.kendala.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> validationErrors,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> missingFields,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> missingDocuments
) {
    public ErrorResponse(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {
        this(timestamp, status, code, message, requestPath(), currentCorrelationId(), fieldErrors, fieldErrors, List.of(), List.of());
    }

    public ErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            Map<String, String> fieldErrors,
            List<String> missingFields,
            List<String> missingDocuments
    ) {
        this(timestamp, status, code, message, requestPath(), currentCorrelationId(), fieldErrors, fieldErrors,
                missingFields, missingDocuments);
    }

    private static String requestPath() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRequestURI();
        }
        return "";
    }

    private static String currentCorrelationId() {
        String value = MDC.get("correlationId");
        return value == null ? "" : value;
    }
}
