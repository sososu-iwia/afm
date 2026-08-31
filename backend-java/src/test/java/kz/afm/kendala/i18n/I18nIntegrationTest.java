package kz.afm.kendala.i18n;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import kz.afm.kendala.common.exception.ApplicationIncompleteException;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.ServiceUnavailableException;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Import(I18nIntegrationTest.ErrorProbeController.class)
class I18nIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void validationUsesRequestedLanguageAndPreservesErrorShape() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk-KZ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Деректер қате"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Өріс міндетті"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.missingFields").doesNotExist())
                .andExpect(jsonPath("$.missingDocuments").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректные данные"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Поле обязательно"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректные данные"));
    }

    @Test
    void requiredDomainErrorsAreLocalizedToKazakh() throws Exception {
        expectKazakh("/test/i18n/otp-invalid", 401, "Растау коды қате");
        expectKazakh(
                "/test/i18n/otp-expired",
                401,
                "Растау кодының мерзімі өтіп кетті"
        );
        expectKazakh("/test/i18n/application-not-found", 404, "Өтінім табылмады");
        expectKazakh("/test/i18n/document-not-found", 404, "Құжат табылмады");
        expectKazakh(
                "/test/i18n/status-transition",
                409,
                "Өтінім мәртебесін өзгертуге тыйым салынған"
        );
        expectKazakh(
                "/test/i18n/commission-conflict",
                409,
                "Комиссия шешімінің қайшылығы"
        );
        expectKazakh(
                "/test/i18n/external-service",
                503,
                "Сыртқы сервис уақытша қолжетімсіз"
        );

        mockMvc.perform(get("/test/i18n/incomplete")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
                        .with(user("probe").roles("ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"))
                .andExpect(jsonPath("$.message").value("Өтінім толық емес"))
                .andExpect(jsonPath("$.missingFields[0]").value("region"))
                .andExpect(jsonPath("$.missingDocuments[0]").value("LAND_DOCUMENT"));
    }

    @Test
    void securityErrorsHonorAcceptLanguageAndUnsupportedLanguageDefaultsToRussian()
            throws Exception {
        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Аутентификация қажет"));

        mockMvc.perform(get("/api/analytics/summary")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
                        .with(user("applicant").roles("APPLICANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message")
                        .value("Қолжетімділікке тыйым салынған"));

        mockMvc.perform(get("/test/i18n/application-not-found")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US")
                        .with(user("probe").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Заявка не найдена"));
    }

    @Test
    void invalidDataAndMalformedJsonReturnLocalized400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("Қазақстан телефон нөмірінің пішімі қате"));

        mockMvc.perform(post("/api/auth/login")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Параметр пішімі қате"));
    }

    private void expectKazakh(String path, int statusCode, String expectedMessage)
            throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "kk")
                        .with(user("probe").roles("ADMIN")))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message").value(expectedMessage));
    }

    @RestController
    @RequestMapping("/test/i18n")
    static class ErrorProbeController {

        @GetMapping("/otp-invalid")
        void otpInvalid() {
            throw new UnauthorizedException("Неверный код", "error.otp.invalid");
        }

        @GetMapping("/otp-expired")
        void otpExpired() {
            throw new UnauthorizedException("Код истёк", "error.otp.expired");
        }

        @GetMapping("/application-not-found")
        void applicationNotFound() {
            throw new NotFoundException(
                    "Заявка не найдена",
                    "error.application-not-found"
            );
        }

        @GetMapping("/document-not-found")
        void documentNotFound() {
            throw new NotFoundException(
                    "Документ не найден",
                    "error.document-not-found"
            );
        }

        @GetMapping("/incomplete")
        void incomplete() {
            throw new ApplicationIncompleteException(
                    List.of("region"),
                    List.of("LAND_DOCUMENT")
            );
        }

        @GetMapping("/status-transition")
        void statusTransition() {
            throw new BusinessRuleException(
                    "Изменение статуса заявки запрещено",
                    "error.status-transition-forbidden"
            );
        }

        @GetMapping("/commission-conflict")
        void commissionConflict() {
            throw new BusinessRuleException(
                    "Конфликт решения комиссии",
                    "error.commission-conflict"
            );
        }

        @GetMapping("/external-service")
        void externalService() {
            throw new ServiceUnavailableException(
                    "UPSTREAM_UNAVAILABLE",
                    "Внешний сервис временно недоступен",
                    "error.external-service"
            );
        }
    }
}
