package kz.afm.kendala.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DomainMetrics {

    private static final Map<String, String> COUNTERS = Map.ofEntries(
            Map.entry("application.created", "applications.created"),
            Map.entry("application.submitted", "applications.submitted"),
            Map.entry("application.approved", "applications.approved"),
            Map.entry("application.rejected", "applications.rejected"),
            Map.entry("application.ready_for_review", "applications.ready.for.review"),
            Map.entry("document.uploaded", "documents.uploaded"),
            Map.entry("scoring.completed", "ai.scoring.completed"),
            Map.entry("scoring.failed", "ai.scoring.failed"),
            Map.entry("ocr.completed", "ai.ocr.completed"),
            Map.entry("ocr.failed", "ai.ocr.failed"),
            Map.entry("duplicate_check.completed", "ai.duplicate.check.completed"),
            Map.entry("duplicate_check.failed", "ai.duplicate.check.failed"),
            Map.entry("llm_conclusion.completed", "ai.llm.conclusion.completed"),
            Map.entry("llm_conclusion.failed", "ai.llm.conclusion.failed"),
            Map.entry("llm_conclusion.failed_optional", "ai.llm.conclusion.failed.optional"),
            Map.entry("notification.sent", "notifications.sent"),
            Map.entry("notification.failed", "notifications.failed"),
            Map.entry("notification.not_configured", "notifications.not.configured"),
            Map.entry("protocol.generated", "protocol.generated")
    );

    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public DomainMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String event) {
        String metric = COUNTERS.get(event);
        if (metric == null) {
            throw new IllegalArgumentException("Unknown metric event: " + event);
        }
        counters.computeIfAbsent(metric, name -> Counter.builder(name).register(registry)).increment();
    }

    public void externalRequest(String service, String operation, String result, Duration duration) {
        Timer.builder("external.request.duration")
                .tag("service", service)
                .tag("operation", operation)
                .tag("result", result)
                .register(registry)
                .record(duration);
    }
}
