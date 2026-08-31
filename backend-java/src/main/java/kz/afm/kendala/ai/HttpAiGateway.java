package kz.afm.kendala.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kz.afm.kendala.ai.dto.AiDuplicateCheckRequest;
import kz.afm.kendala.ai.dto.AiDuplicateCheckResponse;
import kz.afm.kendala.ai.dto.AiLlmConclusionRequest;
import kz.afm.kendala.ai.dto.AiLlmConclusionResponse;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreRequest;
import kz.afm.kendala.ai.dto.AiScoreResponse;
import kz.afm.kendala.observability.DomainMetrics;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HttpAiGateway implements AiGateway {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String internalApiKey;
    private final Duration requestTimeout;
    private final int failureThreshold;
    private final Duration circuitOpenDuration;
    private final DomainMetrics metrics;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant circuitOpenUntil;

    public HttpAiGateway(
            ObjectMapper objectMapper,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.internal-api-key:}") String internalApiKey,
            @Value("${app.ai.connect-timeout-seconds:3}") long connectTimeoutSeconds,
            @Value("${app.ai.request-timeout-seconds:30}") long requestTimeoutSeconds,
            @Value("${app.ai.failure-threshold:3}") int failureThreshold,
            @Value("${app.ai.circuit-open-seconds:30}") long circuitOpenSeconds,
            DomainMetrics metrics
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalApiKey = internalApiKey;
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.failureThreshold = failureThreshold;
        this.circuitOpenDuration = Duration.ofSeconds(circuitOpenSeconds);
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
    }

    @Override
    public AiScoreResponse score(AiScoreRequest request) {
        ensureConfiguredAndAvailable();
        try {
            HttpRequest httpRequest = baseRequest("/internal/score")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                    .build();
            return send(httpRequest, AiScoreResponse.class);
        } catch (IOException e) {
            throw recordFailure("AI_SERIALIZATION_ERROR", "Не удалось сформировать запрос скоринга", false, e);
        }
    }

    @Override
    public AiOcrResponse ocr(UUID documentId, String fileName, String contentType, byte[] content) {
        ensureConfiguredAndAvailable();
        String boundary = "KendalaBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, fileName, contentType, content);
        HttpRequest request = baseRequest("/internal/ocr?documentId=" + documentId)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request, AiOcrResponse.class);
    }

    @Override
    public AiDuplicateCheckResponse duplicateCheck(AiDuplicateCheckRequest request) {
        ensureConfiguredAndAvailable();
        try {
            HttpRequest httpRequest = baseRequest("/internal/duplicates")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                    .build();
            return send(httpRequest, AiDuplicateCheckResponse.class);
        } catch (IOException e) {
            throw recordFailure("AI_SERIALIZATION_ERROR", "Не удалось сформировать запрос проверки дублей", false, e);
        }
    }

    @Override
    public AiLlmConclusionResponse llmConclusion(AiLlmConclusionRequest request) {
        ensureConfiguredAndAvailable();
        try {
            HttpRequest httpRequest = baseRequest("/internal/llm-conclusion")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(request)))
                    .build();
            return send(httpRequest, AiLlmConclusionResponse.class);
        } catch (IOException e) {
            throw recordFailure("AI_SERIALIZATION_ERROR", "Не удалось сформировать запрос LLM-заключения", false, e);
        }
    }

    private HttpRequest.Builder baseRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("X-Internal-Api-Key", internalApiKey);
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            builder.header("X-Correlation-ID", correlationId);
        }
        return builder;
    }

    private <T> T send(HttpRequest request, Class<T> responseType) {
        Instant startedAt = Instant.now();
        String result = "failure";
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                consecutiveFailures.set(0);
                circuitOpenUntil = null;
                result = "success";
                return objectMapper.readValue(response.body(), responseType);
            }
            boolean retryable = response.statusCode() >= 500 || response.statusCode() == 429;
            String message = safeRemoteMessage(response.body());
            throw recordFailure("AI_HTTP_" + response.statusCode(), message, retryable, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw recordFailure("AI_INTERRUPTED", "Вызов AI-сервиса прерван", true, e);
        } catch (IOException e) {
            throw recordFailure("AI_UNAVAILABLE", "AI-сервис недоступен", true, e);
        } finally {
            metrics.externalRequest(
                    "ai",
                    request.uri().getPath(),
                    result,
                    Duration.between(startedAt, Instant.now())
            );
        }
    }

    private void ensureConfiguredAndAvailable() {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            throw new AiGatewayException("AI_NOT_CONFIGURED", "AI integration is not configured", false);
        }
        Instant openUntil = circuitOpenUntil;
        if (openUntil != null && Instant.now().isBefore(openUntil)) {
            throw new AiGatewayException("AI_CIRCUIT_OPEN", "AI service circuit breaker is open", true);
        }
    }

    private AiGatewayException recordFailure(
            String code, String message, boolean retryable, Throwable cause
    ) {
        if (retryable && consecutiveFailures.incrementAndGet() >= failureThreshold) {
            circuitOpenUntil = Instant.now().plus(circuitOpenDuration);
        }
        String safeMessage = message == null || message.isBlank() ? "AI service request failed" : message;
        if (safeMessage.length() > 500) safeMessage = safeMessage.substring(0, 500);
        return cause == null
                ? new AiGatewayException(code, safeMessage, retryable)
                : new AiGatewayException(code, safeMessage, retryable, cause);
    }

    private String safeRemoteMessage(byte[] body) {
        try {
            var node = objectMapper.readTree(body);
            String detail = node.path("detail").asText("");
            return detail.isBlank() ? "AI service returned an error" : detail;
        } catch (Exception ignored) {
            return "AI service returned an error";
        }
    }

    private byte[] multipart(String boundary, String fileName, String contentType, byte[] content) {
        String safeName = fileName.replaceAll("[\\r\\n\"\\\\]", "_");
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(content);
            output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to construct multipart request", impossible);
        }
    }
}
