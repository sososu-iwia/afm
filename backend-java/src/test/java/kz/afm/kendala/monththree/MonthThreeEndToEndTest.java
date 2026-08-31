package kz.afm.kendala.monththree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kz.afm.kendala.ai.AiGateway;
import kz.afm.kendala.ai.AiOperationType;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.ApplicationSnapshotRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.dto.AiDuplicateCheckRequest;
import kz.afm.kendala.ai.dto.AiDuplicateCheckResponse;
import kz.afm.kendala.ai.dto.AiLlmConclusionRequest;
import kz.afm.kendala.ai.dto.AiLlmConclusionResponse;
import kz.afm.kendala.ai.dto.AiOcrResponse;
import kz.afm.kendala.ai.dto.AiScoreRequest;
import kz.afm.kendala.ai.dto.AiScoreResponse;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.AdditionalDocumentRequestRepository;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.audit.AuditLogRepository;
import kz.afm.kendala.auth.service.FakeSmsSender;
import kz.afm.kendala.auth.service.JwtService;
import kz.afm.kendala.notification.NotificationOutboxRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MonthThreeEndToEndTest {

    private static final UUID CHAIRMAN_ID =
            UUID.fromString("93000000-0000-0000-0000-000000000001");
    private static final String APPLICANT_PHONE = "+77003334455";
    private static final byte[] PDF_CONTENT =
            "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);

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
    ObjectMapper objectMapper;

    @Autowired
    FakeSmsSender smsSender;

    @Autowired
    JwtService jwtService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AiProcessingJobRepository aiJobRepository;

    @Autowired
    ApplicationSnapshotRepository snapshotRepository;

    @Autowired
    AdditionalDocumentRequestRepository additionalDocumentRequestRepository;

    @Autowired
    ApplicationRepository applicationRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    NotificationOutboxRepository notificationRepository;

    @MockBean
    AiGateway aiGateway;

    @BeforeEach
    void setUp() {
        createChairman();
        when(aiGateway.score(any())).thenAnswer(invocation -> {
            AiScoreRequest request = invocation.getArgument(0);
            return new AiScoreResponse(
                    request.applicationId(),
                    new BigDecimal("91.25"),
                    "LOW",
                    new BigDecimal("900000.00"),
                    objectMapper.createArrayNode()
                            .addObject()
                            .put("factor", "verified_documents"),
                    "Deterministic test-only scoring conclusion"
            );
        });
        when(aiGateway.ocr(any(), any(), any(), any())).thenAnswer(invocation ->
                new AiOcrResponse(invocation.getArgument(0), true, List.of(),
                        "Оқылатын құжат. Читаемый документ.", new BigDecimal("96.00"), false));
        when(aiGateway.duplicateCheck(any())).thenAnswer(invocation -> {
            AiDuplicateCheckRequest request = invocation.getArgument(0);
            return new AiDuplicateCheckResponse(
                    request.newApplication().applicationId(), false, null, null, List.of(), List.of());
        });
        when(aiGateway.llmConclusion(any())).thenAnswer(invocation -> {
            AiLlmConclusionRequest request = invocation.getArgument(0);
            return new AiLlmConclusionResponse(
                    request.applicationId(), "LLM optional conclusion mocked in test scope");
        });
    }

    @Test
    void completeApplicationWorkflowIsVisibleInRegistryAnalyticsAuditAndOutbox()
            throws Exception {
        registerApplicant();
        String otp = smsSender.getLastCode(APPLICANT_PHONE);
        assertThat(otp).matches("\\d{6}");

        String applicantToken = verifyAndGetToken(otp);
        String chairmanToken = jwtService.generateAccessToken(
                CHAIRMAN_ID,
                UserRole.CHAIRMAN
        );

        UUID applicationId = createDraft(applicantToken);
        uploadDocument(
                applicantToken,
                applicationId,
                "iin.pdf",
                "IIN_CERTIFICATE"
        );
        uploadDocument(
                applicantToken,
                applicationId,
                "land.pdf",
                "LAND_CERTIFICATE"
        );

        mockMvc.perform(get("/api/applications/{id}/completeness", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.missingFields").isEmpty())
                .andExpect(jsonPath("$.missingDocuments").isEmpty());

        mockMvc.perform(post("/api/applications/{id}/submit", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionRevision").value(1));

        awaitReady(applicationId, 1);
        String revisionOneSnapshot = snapshotRepository
                .findByApplicationIdAndSubmissionRevision(applicationId, 1)
                .orElseThrow()
                .getSnapshotJson();

        mockMvc.perform(get("/api/commission/applications/{id}", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.documents.length()").value(2));

        awaitScore(applicationId);
        mockMvc.perform(get("/api/applications/{id}/score", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.score").value(91.25))
                .andExpect(jsonPath("$.result.riskCategory").value("LOW"));

        mockMvc.perform(post(
                                "/api/commission/applications/{id}/request-documents",
                                applicationId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Добавьте банковскую выписку","documentTypes":["BANK_STATEMENT"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("ADDITIONAL_DOCUMENTS_REQUESTED"));

        uploadDocument(
                applicantToken,
                applicationId,
                "bank.pdf",
                "BANK_STATEMENT"
        );
        mockMvc.perform(post("/api/applications/{id}/submit", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionRevision").value(2));

        awaitReady(applicationId, 2);
        assertThat(snapshotRepository.findByApplicationIdOrderBySubmissionRevisionAsc(applicationId))
                .hasSize(2);
        assertThat(snapshotRepository.findByApplicationIdAndSubmissionRevision(applicationId, 1)
                .orElseThrow().getSnapshotJson()).isEqualTo(revisionOneSnapshot);
        assertThat(aiJobRepository.findByOperationTypeAndApplicationId(
                        AiOperationType.SCORING, applicationId))
                .extracting(job -> job.getSubmissionRevision())
                .contains(1, 2);
        assertThat(additionalDocumentRequestRepository
                .findByApplicationIdOrderByCreatedAtAsc(applicationId))
                .singleElement()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(AdditionalDocumentRequestStatus.FULFILLED);
                    assertThat(request.getSourceSubmissionRevision()).isEqualTo(1);
                    assertThat(request.getFulfilledBySubmissionRevision()).isEqualTo(2);
                });

        mockMvc.perform(get("/api/applications/{id}/processing", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionRevision").value(2))
                .andExpect(jsonPath("$.scoring.status").value("COMPLETED"));

        String longReason = "Құжаттар толық, комиссия мақұлдады. ".repeat(50).strip();
        mockMvc.perform(post("/api/commission/applications/{id}/approve", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reason", longReason,
                                "approvedAmount", new BigDecimal("900000.00")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(patch("/api/commission/applications/{id}/publish", applicationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken)))
                .andExpect(status().isOk());

        byte[] protocol = mockMvc.perform(post(
                                "/api/commission/applications/{id}/generate-protocol",
                                applicationId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_PDF_VALUE
                ))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(protocol, 0, 4, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        assertProtocolTextIsUnicodeAndMultipage(protocol);

        mockMvc.perform(get("/api/public/approved-applications")
                        .param("region", "Akmola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].approvedAmount").value(900000.00))
                .andExpect(jsonPath("$.content[0].scoringCategory").value("LOW"))
                .andExpect(jsonPath("$.content[0].applicationNumber").isNotEmpty())
                .andExpect(jsonPath("$.content[0].iinOrBin").doesNotExist())
                .andExpect(jsonPath("$.content[0].applicantId").doesNotExist());

        mockMvc.perform(get("/api/analytics/summary")
                        .param("region", "Akmola")
                        .header(HttpHeaders.AUTHORIZATION, bearer(chairmanToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.APPROVED").value(1))
                .andExpect(jsonPath("$.totalApprovedAmount").value(900000.00))
                .andExpect(jsonPath("$.approvedDecisions").value(1))
                .andExpect(jsonPath("$.additionalDocumentsRequestedDecisions").value(1))
                .andExpect(jsonPath("$.completedAiScoringApplications").value(1))
                .andExpect(jsonPath("$.failedAiTasks").value(0));

        Set<String> actions = auditLogRepository.findAll().stream()
                .map(log -> log.getAction())
                .collect(Collectors.toSet());
        assertThat(actions).contains(
                "APPLICATION_CREATED",
                "DOCUMENT_UPLOADED",
                "APPLICATION_SUBMITTED",
                "AI_WORKFLOW_STARTED",
                "SCORING_COMPLETED",
                "COMMISSION_DECISION"
        );
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(12);
        assertThat(notificationRepository.count()).isGreaterThanOrEqualTo(4);
    }

    private void assertProtocolTextIsUnicodeAndMultipage(byte[] protocol) throws IOException {
        try (PDDocument document = Loader.loadPDF(protocol)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
            assertThat(text)
                    .contains("Протокол заседания кредитной комиссии")
                    .contains("Кредиттік комиссия")
                    .contains("Құжаттар толық")
                    .doesNotContain("?");
        }
    }

    private void registerApplicant() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone":"87003334455",
                                  "fullName":"E2E Applicant",
                                  "email":"e2e-applicant@example.org"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    private String verifyAndGetToken(String otp) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                                .put("phone", APPLICANT_PHONE)
                                .put("code", otp)
                                .put("purpose", "REGISTRATION"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private UUID createDraft(String applicantToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "iinOrBin":"123456789012",
                                  "region":"Akmola",
                                  "productionType":"GRAIN",
                                  "activityType":"CROP_PRODUCTION",
                                  "applicantCategory":"PEASANT_FARM",
                                  "landArea":100.00,
                                  "requestedAmount":1000000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(body.path("id").asText());
    }

    private void uploadDocument(
            String applicantToken,
            UUID applicationId,
            String fileName,
            String documentType
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                MediaType.APPLICATION_PDF_VALUE,
                PDF_CONTENT
        );
        mockMvc.perform(multipart("/api/applications/{id}/documents", applicationId)
                        .file(file)
                        .param("documentType", documentType)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value(documentType));
    }

    private void awaitScore(UUID applicationId) throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            var latest = aiJobRepository
                    .findFirstByOperationTypeAndApplicationIdOrderByCreatedAtDesc(
                            AiOperationType.SCORING,
                            applicationId
                    );
            if (latest.isPresent()
                    && latest.get().getStatus() == AiTaskStatus.COMPLETED) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("AI scoring did not complete");
    }

    private void awaitReady(UUID applicationId, int revision) throws InterruptedException {
        for (int attempt = 0; attempt < 300; attempt++) {
            var application = applicationRepository.findById(applicationId).orElseThrow();
            if (application.getSubmissionRevision() == revision
                    && application.getStatus() == ApplicationStatus.IN_REVIEW) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Application did not become ready for revision " + revision);
    }

    private void createChairman() {
        User chairman = new User();
        chairman.setId(CHAIRMAN_ID);
        chairman.setPhone("+77009990001");
        chairman.setFullName("E2E Chairman");
        chairman.setRole(UserRole.CHAIRMAN);
        chairman.setActive(true);
        chairman.setVerifiedAt(Instant.now());
        userRepository.save(chairman);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
