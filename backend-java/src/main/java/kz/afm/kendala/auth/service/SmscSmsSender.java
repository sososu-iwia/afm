package kz.afm.kendala.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import kz.afm.kendala.common.exception.ServiceUnavailableException;
import kz.afm.kendala.observability.DomainMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Отправка OTP через SMSC.
 *
 * По умолчанию используется казахстанский узел smsc.kz — тот же, что указан
 * в интерфейсе как способ авторизации. Узел настраивается на случай, если
 * договор заключён с другим доменом группы.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "smsc")
public class SmscSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(SmscSmsSender.class);

    private final String login;
    private final String password;
    private final String sender;
    private final String endpoint;
    private final HttpClient httpClient;
    private final DomainMetrics metrics;
    private final ObjectMapper objectMapper;

    public SmscSmsSender(
            @Value("${SMSC_LOGIN:}") String login,
            @Value("${SMSC_PASSWORD:}") String password,
            @Value("${SMSC_SENDER:}") String sender,
            @Value("${SMSC_URL:https://smsc.kz/sys/send.php}") String endpoint,
            DomainMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.login = login;
        this.password = password;
        this.sender = sender;
        this.endpoint = endpoint;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public SmsDeliveryResult send(String to, String message) {
        Instant startedAt = Instant.now();
        String result = "failure";
        try {
            if (login.isBlank() || password.isBlank()) {
                result = "not_configured";
                return SmsDeliveryResult.notConfigured("SMS_NOT_CONFIGURED");
            }

            // Пароль передаём телом POST, а не в query: строка запроса оседает
            // в логах прокси и в истории обращений.
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(buildForm(to, message), StandardCharsets.UTF_8));
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                requestBuilder.header("X-Correlation-ID", correlationId);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                log.error("SMSC request failed, status={}", response.statusCode());
                return SmsDeliveryResult.failed("SMS_UNAVAILABLE");
            }

            JsonNode body = parse(response.body());
            if (body == null) {
                log.error("SMSC returned a response that could not be parsed");
                return SmsDeliveryResult.failed("SMS_UNAVAILABLE");
            }
            if (body.hasNonNull("error")) {
                // Текст ошибки не логируем целиком: он может содержать номер получателя.
                log.error("SMSC rejected the message, error_code={}", body.path("error_code").asInt(-1));
                return SmsDeliveryResult.failed("SMS_UNAVAILABLE");
            }

            result = "success";
            String deliveryId = body.hasNonNull("id") ? body.get("id").asText() : null;
            return SmsDeliveryResult.sent(deliveryId);
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("SMSC send failed", e);
            return SmsDeliveryResult.failed("SMS_UNAVAILABLE");
        } finally {
            metrics.externalRequest(
                    "smsc",
                    "send",
                    result,
                    Duration.between(startedAt, Instant.now())
            );
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            return null;
        }
    }

    /** fmt=3 — ответ в JSON, поэтому успех и отказ различимы без разбора текста. */
    private String buildForm(String to, String message) {
        StringBuilder form = new StringBuilder()
                .append("login=").append(encode(login))
                .append("&psw=").append(encode(password))
                .append("&phones=").append(encode(to))
                .append("&mes=").append(encode(message))
                .append("&fmt=3");
        if (!sender.isBlank()) {
            form.append("&sender=").append(encode(sender));
        }
        return form.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
