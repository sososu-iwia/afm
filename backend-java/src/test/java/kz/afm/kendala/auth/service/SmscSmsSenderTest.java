package kz.afm.kendala.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import kz.afm.kendala.observability.DomainMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmscSmsSenderTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastQuery = new AtomicReference<>();
    private volatile String response = "{\"id\":42,\"cnt\":1}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sys/send.php", exchange -> {
            lastQuery.set(exchange.getRequestURI().getQuery());
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private SmscSmsSender sender() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/sys/send.php";
        return new SmscSmsSender(
                "demo-login", "demo-password", "KenDala", url,
                new DomainMetrics(new SimpleMeterRegistry()), new ObjectMapper());
    }

    @Test
    void sendsCredentialsInTheBodyAndNotInTheQueryString() {
        SmsDeliveryResult result = sender().send("+77000000001", "Код: 123456");

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(result.providerMessageId()).isEqualTo("42");
        // Пароль не должен попадать в строку запроса: она оседает в логах прокси.
        assertThat(lastQuery.get()).isNull();
        assertThat(lastBody.get()).contains("psw=demo-password");
        assertThat(lastBody.get()).contains("fmt=3");
    }

    @Test
    void reportsFailureWhenSmscAnswersWithAnError() {
        response = "{\"error\":\"invalid number\",\"error_code\":7}";

        SmsDeliveryResult result = sender().send("+77000000001", "Код: 123456");

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("SMS_UNAVAILABLE");
    }

    @Test
    void reportsNotConfiguredWhenCredentialsAreMissing() {
        SmscSmsSender configless = new SmscSmsSender(
                "", "", "", "http://127.0.0.1:1/sys/send.php",
                new DomainMetrics(new SimpleMeterRegistry()), new ObjectMapper());

        SmsDeliveryResult result = configless.send("+77000000001", "Код: 123456");

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.NOT_CONFIGURED);
        assertThat(result.errorCode()).isEqualTo("SMS_NOT_CONFIGURED");
    }
}
