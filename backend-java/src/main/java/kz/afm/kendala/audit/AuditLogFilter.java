package kz.afm.kendala.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class AuditLogFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditLogFilter.class);
    private final AuditService auditService;

    public AuditLogFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            structuredLog(request, response, startedAt);
            if (shouldAudit(request)) {
                record(request, response);
            }
        }
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String method = request.getMethod();
        return "POST".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)
                || request.getRequestURI().endsWith("/export");
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String actor = authentication != null && authentication.isAuthenticated()
                    ? authentication.getName() : "anonymous";
            String role = authentication != null && !authentication.getAuthorities().isEmpty()
                    ? authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                    : "ANONYMOUS";
            EntityRef ref = entityRef(request.getRequestURI());
            auditService.record(
                    actor, role, request.getMethod() + " " + request.getRequestURI(),
                    ref.type(), ref.id(), null, Map.of("httpStatus", response.getStatus()),
                    request.getRemoteAddr(), MDC.get("correlationId"),
                    response.getStatus() < 400 ? "SUCCESS" : "FAILURE",
                    "USER",
                    response.getStatus() < 400 ? null : "HTTP_" + response.getStatus());
        } catch (Exception exception) {
            log.error("Audit log persistence failed", exception);
        }
    }

    private void structuredLog(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actorRole = authentication != null && !authentication.getAuthorities().isEmpty()
                ? authentication.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")
                : "ANONYMOUS";
        Object matchedPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String operation = request.getMethod() + " "
                + (matchedPattern == null ? request.getRequestURI() : matchedPattern);
        String result = response.getStatus() < 400 ? "SUCCESS" : "FAILURE";
        long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
        MDC.put("actorRole", actorRole);
        MDC.put("operation", operation);
        MDC.put("result", result);
        MDC.put("durationMs", Long.toString(durationMs));
        try {
            log.info("request completed");
        } finally {
            MDC.remove("actorRole");
            MDC.remove("operation");
            MDC.remove("result");
            MDC.remove("durationMs");
        }
    }

    private EntityRef entityRef(String uri) {
        String[] parts = uri.split("/");
        for (int index = 0; index < parts.length; index++) {
            if (isUuid(parts[index])) {
                String type = index > 0 && "documents".equals(parts[index - 1])
                        ? "DOCUMENT" : "APPLICATION";
                return new EntityRef(type, parts[index]);
            }
        }
        if (uri.contains("/auth/")) return new EntityRef("AUTH", "-");
        if (uri.endsWith("/export")) return new EntityRef("APPLICATION_EXPORT", "-");
        return new EntityRef("HTTP_REQUEST", "-");
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private record EntityRef(String type, String id) {}
}
