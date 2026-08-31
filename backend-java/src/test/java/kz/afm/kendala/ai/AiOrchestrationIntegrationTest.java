package kz.afm.kendala.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreResponse;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.DocumentType;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import kz.afm.kendala.filestore.StorageService;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.documents.required-types=")
class AiOrchestrationIntegrationTest {
    private static final UUID USER_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("70000000-0000-0000-0000-000000000002");
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
    @Autowired ObjectMapper objectMapper;
    @Autowired AiProcessingJobRepository jobRepository;
    @Autowired ApplicationScoreRepository scoreRepository;
    @Autowired DocumentOcrResultRepository ocrRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired UserRepository userRepository;
    @Autowired StorageService storageService;
    @MockBean AiGateway aiGateway;

    @AfterEach
    void waitForAsyncWorkers() throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            boolean settled = jobRepository.findAll().stream()
                    .noneMatch(job -> job.getStatus() == AiTaskStatus.PENDING
                            || job.getStatus() == AiTaskStatus.PROCESSING
                            || job.getStatus() == AiTaskStatus.FAILED_RETRYABLE);
            if (settled) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("AI workers did not settle after test");
    }

    @BeforeEach
    void setUp() {
        ocrRepository.deleteAll();
        scoreRepository.deleteAll();
        jobRepository.deleteAll();
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        createUser(USER_ID, "+77070000001");
        createUser(OTHER_USER_ID, "+77070000002");
    }

    @Test
    void scoreIsProcessedAsynchronouslyAndPersisted() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        when(aiGateway.score(any())).thenReturn(new AiScoreResponse(
                applicationId, new BigDecimal("87.50"), "LOW", new BigDecimal("900000.00"),
                objectMapper.createArrayNode().addObject().put("factor", "land_area"),
                "Verified model conclusion"));

        mockMvc.perform(post("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.task.jobId").isNotEmpty());

        awaitTerminal(applicationId);

        mockMvc.perform(get("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.task.attemptCount").value(1))
                .andExpect(jsonPath("$.result.score").value(87.50))
                .andExpect(jsonPath("$.result.riskCategory").value("LOW"))
                .andExpect(jsonPath("$.result.llmSummary").value("Verified model conclusion"));
    }

    @Test
    void retryableFailureExhaustsPolicyAndStoresFailedStatus() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        when(aiGateway.score(any())).thenThrow(
                new AiGatewayException("AI_UNAVAILABLE", "AI service unavailable", true));

        mockMvc.perform(post("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isAccepted());

        awaitTerminal(applicationId);

        mockMvc.perform(get("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("FAILED_TERMINAL"))
                .andExpect(jsonPath("$.task.attemptCount").value(3))
                .andExpect(jsonPath("$.task.errorCode").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void applicantCannotReadAnotherApplicantsScore() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        mockMvc.perform(get("/api/applications/{id}/score", applicationId)
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void approvedApplicationCannotTriggerNewScoring() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        Application application = applicationRepository.findById(applicationId).orElseThrow();
        application.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.saveAndFlush(application);

        mockMvc.perform(post("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectedApplicationCannotTriggerNewScoring() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        Application application = applicationRepository.findById(applicationId).orElseThrow();
        application.setStatus(ApplicationStatus.REJECTED);
        applicationRepository.saveAndFlush(application);

        mockMvc.perform(post("/api/applications/{id}/score", applicationId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict());
    }

    @Test
    void ocrResultIsStoredAndReturnedWithoutFakeFallback() throws Exception {
        UUID applicationId = createApplication(USER_ID);
        UUID documentId = createDocument(applicationId);
        when(aiGateway.ocr(any(), any(), any(), any())).thenReturn(new AiOcrResponse(
                documentId, true, List.of(), "Extracted verified text",
                new BigDecimal("92.40"), false));

        mockMvc.perform(post("/api/documents/{id}/ocr", documentId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isAccepted());

        awaitOcrTerminal(documentId);

        mockMvc.perform(get("/api/documents/{id}/ocr", documentId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.readable").value(true))
                .andExpect(jsonPath("$.result.confidence").value(92.40))
                .andExpect(jsonPath("$.result.extractedText").value("Extracted verified text"))
                .andExpect(jsonPath("$.result.checkedAt").isNotEmpty());
    }

    private void awaitTerminal(UUID applicationId) throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            var latest = jobRepository.findFirstByOperationTypeAndApplicationIdOrderByCreatedAtDesc(
                    AiOperationType.SCORING, applicationId);
            if (latest.isPresent() && (latest.get().getStatus() == AiTaskStatus.COMPLETED
                    || latest.get().getStatus() == AiTaskStatus.FAILED
                    || latest.get().getStatus() == AiTaskStatus.FAILED_TERMINAL)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("AI job did not finish");
    }

    private void awaitOcrTerminal(UUID documentId) throws InterruptedException {
        for (int attempt = 0; attempt < 150; attempt++) {
            var latest = jobRepository.findFirstByOperationTypeAndDocumentIdOrderByCreatedAtDesc(
                    AiOperationType.OCR, documentId);
            if (latest.isPresent() && (latest.get().getStatus() == AiTaskStatus.COMPLETED
                    || latest.get().getStatus() == AiTaskStatus.FAILED
                    || latest.get().getStatus() == AiTaskStatus.FAILED_TERMINAL)) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("OCR job did not finish");
    }

    private void createUser(UUID id, String phone) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setFullName("Applicant");
        user.setRole(UserRole.APPLICANT);
        userRepository.save(user);
    }

    private UUID createApplication(UUID applicantId) {
        Application app = new Application();
        app.setApplicationNumber("AI-" + UUID.randomUUID().toString().substring(0, 8));
        app.setApplicant(userRepository.getReferenceById(applicantId));
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setSubmissionRevision(1);
        app.setIinOrBin("123456789012");
        app.setRegion("Akmola");
        app.setProductionType("GRAIN");
        app.setLandArea(new BigDecimal("50.00"));
        app.setRequestedAmount(new BigDecimal("1000000.00"));
        return applicationRepository.save(app).getId();
    }

    private UUID createDocument(UUID applicationId) {
        var stored = storageService.store("document.pdf", "application/pdf", "%PDF-test".getBytes());
        Document document = new Document();
        document.setApplication(applicationRepository.getReferenceById(applicationId));
        document.setDocumentType(DocumentType.OTHER);
        document.setOriginalFileName("document.pdf");
        document.setContentType("application/pdf");
        document.setStorageKey(stored.storageKey());
        document.setSize(stored.size());
        return documentRepository.save(document).getId();
    }
}
