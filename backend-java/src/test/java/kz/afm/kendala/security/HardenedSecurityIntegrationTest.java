package kz.afm.kendala.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.audit.AuditLogRepository;
import kz.afm.kendala.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.request.max-body-bytes=128")
class HardenedSecurityIntegrationTest {

    private static final UUID APPLICANT_ID =
            UUID.fromString("fd000000-0000-0000-0000-000000000001");

    @TempDir
    static Path tempDir;

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.storage.local.path", () -> tempDir.toString());
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    AuditService auditService;
    @Autowired
    AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        User applicant = new User();
        applicant.setId(APPLICANT_ID);
        applicant.setPhone("+77070002001");
        applicant.setFullName("Security Applicant");
        applicant.setRole(UserRole.APPLICANT);
        applicant.setActive(true);
        userRepository.save(applicant);
    }

    @Test
    void protectedAndPrivilegedEndpointsEnforceAuthenticationAndRole() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/commission/applications")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void missingResourceReturns404InsteadOfInternalError() throws Exception {
        String body = mockMvc.perform(get("/definitely-missing-resource")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .contains("NOT_FOUND")
                .doesNotContain("stackTrace")
                .doesNotContain("exception");
    }

    @Test
    void publicRegistryIsAnonymousAndDoesNotExposePersonalFields() throws Exception {
        String body = mockMvc.perform(get("/api/public/approved-applications"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain("phone")
                .doesNotContain("fullName")
                .doesNotContain("iinOrBin")
                .doesNotContain("applicantId")
                .doesNotContain("storageKey");
    }

    @Test
    void oversizedJsonRequestReturns413WithoutStackTrace() throws Exception {
        String body = """
                {"phone":"87070002002","fullName":"%s"}
                """.formatted("x".repeat(256));

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .contains("REQUEST_TOO_LARGE")
                .doesNotContain("stackTrace")
                .doesNotContain("exception");
    }

    @Test
    void auditPayloadRedactsSensitiveFields() {
        User actor = userRepository.findById(APPLICANT_ID).orElseThrow();
        auditService.recordDomain(
                actor,
                "SECURITY_TEST",
                "APPLICATION",
                UUID.randomUUID().toString(),
                null,
                Map.of(
                        "phone", "+77070002001",
                        "iinOrBin", "123456789012",
                        "refreshToken", "secret-refresh-token",
                        "safeField", "visible"
                )
        );

        String stored = auditLogRepository.findAll().getFirst().getNewValue();
        assertThat(stored)
                .contains("[REDACTED]")
                .contains("visible")
                .doesNotContain("+77070002001")
                .doesNotContain("123456789012")
                .doesNotContain("secret-refresh-token");
    }

    @Test
    void excessivePaginationOffsetIsRejected() throws Exception {
        mockMvc.perform(get("/api/applications")
                        .param("page", "10001")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/public/approved-applications")
                        .param("page", "10001"))
                .andExpect(status().isBadRequest());
    }
}
