package kz.afm.kendala.monthtwo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.ai.GeneratedProtocolRepository;
import kz.afm.kendala.ai.AiOperationType;
import kz.afm.kendala.ai.AiProcessingJob;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.ApplicationScore;
import kz.afm.kendala.ai.ApplicationScoreRepository;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DecisionRepository;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.audit.AuditLogRepository;
import kz.afm.kendala.notification.NotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
@TestPropertySource(properties = "app.documents.required-types=")
class MonthTwoIntegrationTest {
    private static final UUID APPLICANT_ID =
            UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CHAIRMAN_ID =
            UUID.fromString("80000000-0000-0000-0000-000000000002");
    private static final UUID SECRETARY_ID =
            UUID.fromString("80000000-0000-0000-0000-000000000003");

    @TempDir static Path tempDir;
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.storage.local.path", () -> tempDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApplicationStatusHistoryRepository historyRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired GeneratedProtocolRepository protocolRepository;
    @Autowired NotificationOutboxRepository notificationRepository;
    @Autowired AuditLogRepository auditRepository;
    @Autowired UserRepository userRepository;
    @Autowired AiProcessingJobRepository aiJobRepository;
    @Autowired ApplicationScoreRepository scoreRepository;

    @BeforeEach
    void setUp() {
        protocolRepository.deleteAll();
        notificationRepository.deleteAll();
        decisionRepository.deleteAll();
        scoreRepository.deleteAll();
        aiJobRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        auditRepository.deleteAll();
        userRepository.deleteAll();
        createUser(APPLICANT_ID, "+77080000001", "applicant@example.org", UserRole.APPLICANT);
        createUser(CHAIRMAN_ID, "+77080000002", null, UserRole.CHAIRMAN);
        createUser(SECRETARY_ID, "+77080000003", null, UserRole.SECRETARY);
    }

    @Test
    void exportReturnsRealXlsx() throws Exception {
        createApplication(ApplicationStatus.SUBMITTED);
        byte[] content = mockMvc.perform(get("/api/commission/applications/export")
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void finalDecisionQueuesSmsAndEmailAndProtocolReturnsRealPdf() throws Exception {
        UUID applicationId = createApplication(ApplicationStatus.IN_REVIEW);
        mockMvc.perform(post("/api/commission/applications/{id}/approve", applicationId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Approved by committee\"}"))
                .andExpect(status().isOk());

        assertThat(notificationRepository.count()).isEqualTo(2);

        byte[] pdf = mockMvc.perform(post(
                                "/api/commission/applications/{id}/generate-protocol", applicationId)
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        assertThat(protocolRepository.count()).isEqualTo(1);
    }

    @Test
    void mutationProducesAuditRecordWithActorIpAndCorrelationId() throws Exception {
        String correlationId = "month-two-audit-1";
        mockMvc.perform(post("/api/applications")
                        .header("X-Correlation-ID", correlationId)
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"123456789012","region":"Akmola",
                                 "productionType":"GRAIN","landArea":50,"requestedAmount":1000000}
                                """))
                .andExpect(status().isCreated());

        assertThat(auditRepository.findAll())
                .filteredOn(log -> "APPLICATION_CREATED".equals(log.getAction()))
                .singleElement()
                .satisfies(log -> {
            assertThat(log.getActor()).isEqualTo(APPLICANT_ID.toString());
            assertThat(log.getActorRole()).isEqualTo("APPLICANT");
            assertThat(log.getEntityType()).isEqualTo("APPLICATION");
            assertThat(log.getEntityId()).isNotEqualTo("-");
            assertThat(log.getIp()).isNotBlank();
            assertThat(log.getCorrelationId()).isEqualTo(correlationId);
            assertThat(log.getPreviousValue()).isEqualTo("null");
            assertThat(log.getNewValue())
                    .contains("\"status\": \"DRAFT\"")
                    .contains("\"region\": \"Akmola\"");
        });
    }

    private UUID createApplication(ApplicationStatus status) {
        Application app = new Application();
        app.setApplicationNumber("M2-" + UUID.randomUUID().toString().substring(0, 8));
        app.setApplicant(userRepository.getReferenceById(APPLICANT_ID));
        app.setStatus(status);
        app.setIinOrBin("123456789012");
        app.setRegion("Akmola");
        app.setProductionType("GRAIN");
        app.setLandArea(new BigDecimal("50.00"));
        app.setRequestedAmount(new BigDecimal("1000000.00"));
        if (status == ApplicationStatus.IN_REVIEW) {
            app.setSubmissionRevision(1);
        }
        app = applicationRepository.saveAndFlush(app);
        if (status == ApplicationStatus.IN_REVIEW) {
            AiProcessingJob job = new AiProcessingJob();
            job.setOperationType(AiOperationType.SCORING);
            job.setApplication(app);
            job.setSubmissionRevision(1);
            job.setStatus(AiTaskStatus.COMPLETED);
            job.setMaxAttempts(3);
            job.setCompletedAt(Instant.now());
            job = aiJobRepository.saveAndFlush(job);

            ApplicationScore score = new ApplicationScore();
            score.setApplicationId(app.getId());
            score.setSubmissionRevision(1);
            score.setJob(job);
            score.setScore(new BigDecimal("88.00"));
            score.setRiskCategory("LOW");
            score.setRecommendedAmount(app.getRequestedAmount());
            score.setTopFactors("[]");
            score.setModelName("month-two-test");
            score.setModelVersion("test-v1");
            score.setModelMetadata("{}");
            score.setCheckedAt(Instant.now());
            scoreRepository.saveAndFlush(score);
        }
        return app.getId();
    }

    private void createUser(UUID id, String phone, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setEmail(email);
        user.setFullName("User " + role);
        user.setRole(role);
        userRepository.save(user);
    }

    private static final class HttpHeaders {
        private static final String CONTENT_TYPE = "Content-Type";
    }
}
