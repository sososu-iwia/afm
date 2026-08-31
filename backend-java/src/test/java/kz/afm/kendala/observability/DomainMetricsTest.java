package kz.afm.kendala.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DomainMetricsTest {

    @Test
    void exposesOnlyTheDeclaredLowCardinalityMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DomainMetrics metrics = new DomainMetrics(registry);

        metrics.increment("application.created");
        metrics.increment("application.submitted");
        metrics.increment("application.approved");
        metrics.increment("application.rejected");
        metrics.increment("application.ready_for_review");
        metrics.increment("document.uploaded");
        metrics.increment("scoring.completed");
        metrics.increment("scoring.failed");
        metrics.increment("ocr.completed");
        metrics.increment("ocr.failed");
        metrics.increment("duplicate_check.completed");
        metrics.increment("duplicate_check.failed");
        metrics.increment("llm_conclusion.completed");
        metrics.increment("llm_conclusion.failed");
        metrics.increment("llm_conclusion.failed_optional");
        metrics.increment("notification.sent");
        metrics.increment("notification.failed");
        metrics.increment("notification.not_configured");
        metrics.increment("protocol.generated");
        metrics.externalRequest("ai", "scoring", "success", Duration.ofMillis(25));

        assertThat(registry.getMeters())
                .extracting(meter -> meter.getId().getName())
                .contains(
                        "applications.created",
                        "applications.submitted",
                        "applications.approved",
                        "applications.rejected",
                        "applications.ready.for.review",
                        "documents.uploaded",
                        "ai.scoring.completed",
                        "ai.scoring.failed",
                        "ai.ocr.completed",
                        "ai.ocr.failed",
                        "ai.duplicate.check.completed",
                        "ai.duplicate.check.failed",
                        "ai.llm.conclusion.completed",
                        "ai.llm.conclusion.failed",
                        "ai.llm.conclusion.failed.optional",
                        "notifications.sent",
                        "notifications.failed",
                        "notifications.not.configured",
                        "protocol.generated",
                        "external.request.duration"
                );
        assertThat(registry.get("external.request.duration")
                .tags("service", "ai", "operation", "scoring", "result", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("applicationId")
                                || tag.getKey().equals("userId")));
    }

    @Test
    void rejectsUnregisteredMetricEvents() {
        DomainMetrics metrics = new DomainMetrics(new SimpleMeterRegistry());

        assertThatThrownBy(() -> metrics.increment("user.123"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
