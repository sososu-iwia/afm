package kz.afm.kendala.stageC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DecisionRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.application.service.ApplicationNumberGenerator;
import kz.afm.kendala.ai.AiOperationType;
import kz.afm.kendala.ai.AiProcessingJob;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.ApplicationScore;
import kz.afm.kendala.ai.ApplicationScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.documents.required-types=IIN_CERTIFICATE,LAND_CERTIFICATE")
class CommissionIntegrationTest {

    static final UUID APPLICANT_ID = UUID.fromString("aa000000-0000-0000-0000-000000000001");
    static final UUID MEMBER_ID    = UUID.fromString("aa000000-0000-0000-0000-000000000002");
    static final UUID CHAIRMAN_ID  = UUID.fromString("aa000000-0000-0000-0000-000000000003");
    static final UUID SECRETARY_ID = UUID.fromString("aa000000-0000-0000-0000-000000000004");
    static final UUID OTHER_APP_ID = UUID.fromString("aa000000-0000-0000-0000-000000000005");

    @TempDir static Path tempDir;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("app.storage.local.path", () -> tempDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApplicationStatusHistoryRepository historyRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired ApplicationNumberGenerator numberGenerator;
    @Autowired AiProcessingJobRepository aiJobRepository;
    @Autowired ApplicationScoreRepository scoreRepository;

    @BeforeEach
    void setUp() {
        decisionRepository.deleteAll();
        scoreRepository.deleteAll();
        aiJobRepository.deleteAll();
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        createUser(APPLICANT_ID, "+77090000001", UserRole.APPLICANT);
        createUser(MEMBER_ID,    "+77090000002", UserRole.COMMISSION_MEMBER);
        createUser(CHAIRMAN_ID,  "+77090000003", UserRole.CHAIRMAN);
        createUser(SECRETARY_ID, "+77090000004", UserRole.SECRETARY);
        createUser(OTHER_APP_ID, "+77090000005", UserRole.APPLICANT);
    }

    // applicant не видит commission list
    @Test
    void applicant_cannotAccessCommissionList() throws Exception {
        mockMvc.perform(get("/api/commission/applications")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isForbidden());
    }

    // member видит commission list
    @Test
    void member_canSeeCommissionList() throws Exception {
        createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(get("/api/commission/applications")
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // member не может approve
    @Test
    void member_cannotApprove() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // secretary не может reject
    @Test
    void secretary_cannotReject() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/reject", appId)
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // chairman approve → статус APPROVED, decision сохраняется, history создаётся
    @Test
    void chairman_approve_savesDecisionAndHistory() throws Exception {
        UUID appId = createReviewableApp(APPLICANT_ID);
        long decisionsBefore = decisionRepository.count();
        long historyBefore   = historyRepository.count();

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Всё хорошо\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisions[0].decidedByName").isNotEmpty());

        assertThat(decisionRepository.count()).isGreaterThan(decisionsBefore);
        assertThat(historyRepository.count()).isGreaterThan(historyBefore);
        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.APPROVED);
    }

    // chairman reject
    @Test
    void chairman_reject_statusRejected() throws Exception {
        UUID appId = createReviewableApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/reject", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Недостаточно документов\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // chairman request-documents
    @Test
    void chairman_requestDocuments_statusChanged() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/request-documents", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Нужен оригинал ИИН\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADDITIONAL_DOCUMENTS_REQUESTED"));
    }

    @Test
    void submittedApplicationCannotBeApprovedBeforeAutomaticProcessing() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Too early\"}"))
                .andExpect(status().isConflict());
    }

    // неправильный переход запрещён (APPROVED → reject)
    @Test
    void finalStatus_cannotBeChanged() throws Exception {
        UUID appId = createReviewableApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/commission/applications/{id}/reject", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Финальный статус уже установлен\"}"))
                .andExpect(status().isConflict());
    }

    // фильтры выполняются в БД
    @Test
    void filter_byStatus_returnsCorrectPage() throws Exception {
        createSubmittedApp(APPLICANT_ID);

        MvcResult r = mockMvc.perform(get("/api/commission/applications")
                        .param("status", "SUBMITTED")
                        .param("page", "1").param("size", "10")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andReturn();

        var root = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(root.get("content").size()).isGreaterThan(0);
        root.get("content").forEach(item ->
                assertThat(item.get("status").asText()).isEqualTo("SUBMITTED"));
    }

    // pagination работает
    @Test
    void pagination_sizeOneReturnsOnePage() throws Exception {
        createSubmittedApp(APPLICANT_ID);
        createSubmittedApp(OTHER_APP_ID);

        mockMvc.perform(get("/api/commission/applications")
                        .param("page", "1").param("size", "1")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    // commission role видит документ заявки
    @Test
    void member_canDownloadDocument() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);

        // upload a doc as applicant (before submit — need DRAFT, so use new app)
        UUID draftId = createDraftApp(APPLICANT_ID);
        UUID docId = uploadDoc(draftId, APPLICANT_ID);

        // submit draft to make it visible in commission
        forceSubmit(draftId);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isOk());
    }

    // applicant не видит чужой документ
    @Test
    void applicant_cannotSeeOtherAppDocument() throws Exception {
        UUID draftId = createDraftApp(APPLICANT_ID);
        UUID docId = uploadDoc(draftId, APPLICANT_ID);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(OTHER_APP_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // пользователь не может назначить себе роль через register (роль всегда APPLICANT)
    @Test
    void register_alwaysCreatesApplicant() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"87099990099\",\"fullName\":\"Hacker\",\"role\":\"CHAIRMAN\"}"))
                .andExpect(status().isAccepted());

        var user = userRepository.findByPhone("+77099990099").orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.APPLICANT);
    }

    // DRAFT не попадает в список комиссии
    @Test
    void list_doesNotContainDraft() throws Exception {
        createDraftApp(APPLICANT_ID);

        mockMvc.perform(get("/api/commission/applications")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // WITHDRAWN не попадает в список комиссии
    @Test
    void list_doesNotContainWithdrawn() throws Exception {
        UUID appId = createDraftApp(APPLICANT_ID);
        forceStatus(appId, ApplicationStatus.WITHDRAWN);

        mockMvc.perform(get("/api/commission/applications")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // фильтр по региону
    @Test
    void filter_byRegion_returnsMatchingItems() throws Exception {
        createSubmittedAppWith(APPLICANT_ID, "Almaty", "1000000");
        createSubmittedAppWith(OTHER_APP_ID, "Astana", "1000000");

        MvcResult r = mockMvc.perform(get("/api/commission/applications")
                        .param("region", "Almaty")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(root.get("totalElements").asLong()).isEqualTo(1);
        assertThat(root.get("content").get(0).get("region").asText()).isEqualTo("Almaty");
    }

    // фильтр по minAmount
    @Test
    void filter_byMinAmount_returnsAboveThreshold() throws Exception {
        createSubmittedAppWith(APPLICANT_ID, "Almaty", "500000");
        createSubmittedAppWith(OTHER_APP_ID, "Almaty", "2000000");

        mockMvc.perform(get("/api/commission/applications")
                        .param("minAmount", "1000000")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // фильтр по maxAmount
    @Test
    void filter_byMaxAmount_returnsBelowThreshold() throws Exception {
        createSubmittedAppWith(APPLICANT_ID, "Almaty", "500000");
        createSubmittedAppWith(OTHER_APP_ID, "Almaty", "2000000");

        mockMvc.perform(get("/api/commission/applications")
                        .param("maxAmount", "1000000")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // комбинированный фильтр status + region
    @Test
    void filter_combined_statusAndRegion() throws Exception {
        UUID almaty = createSubmittedAppWith(APPLICANT_ID, "Almaty", "1000000");
        createSubmittedAppWith(OTHER_APP_ID, "Astana", "1000000");

        MvcResult r = mockMvc.perform(get("/api/commission/applications")
                        .param("status", "SUBMITTED")
                        .param("region", "Almaty")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(root.get("totalElements").asLong()).isEqualTo(1);
        assertThat(root.get("content").get(0).get("id").asText()).isEqualTo(almaty.toString());
    }

    // сортировка по applicationNumber работает без ошибок
    @Test
    void list_sortByApplicationNumber_doesNotCrash() throws Exception {
        createSubmittedApp(APPLICANT_ID);

        mockMvc.perform(get("/api/commission/applications")
                        .param("sortBy", "applicationNumber")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    // неизвестный sortBy → безопасный дефолт, не 500
    @Test
    void list_unknownSortBy_fallsBackSafely() throws Exception {
        createSubmittedApp(APPLICANT_ID);

        mockMvc.perform(get("/api/commission/applications")
                        .param("sortBy", "hackerField")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk());
    }

    // невалидный enum статуса → 400
    @Test
    void list_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/commission/applications")
                        .param("status", "NOT_A_VALID_STATUS")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isBadRequest());
    }

    // minAmount > maxAmount → 400 INVALID_FILTER
    @Test
    void list_minAmountGreaterThanMax_returns400() throws Exception {
        mockMvc.perform(get("/api/commission/applications")
                        .param("minAmount", "1000000")
                        .param("maxAmount", "500000")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }

    // size > MAX_PAGE_SIZE → обрезается до 100
    @Test
    void list_size200_cappedAtMaximum() throws Exception {
        createSubmittedApp(APPLICANT_ID);

        mockMvc.perform(get("/api/commission/applications")
                        .param("size", "200")
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    // детальная карточка содержит все ключевые поля
    @Test
    void detail_returnsAllFields() throws Exception {
        UUID appId = createDraftApp(APPLICANT_ID);
        uploadDoc(appId, APPLICANT_ID);
        makeReviewable(appId);

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Okay\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/commission/applications/{id}", appId)
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId.toString()))
                .andExpect(jsonPath("$.applicantId").isNotEmpty())
                .andExpect(jsonPath("$.applicantName").isNotEmpty())
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.statusHistory").isArray())
                .andExpect(jsonPath("$.decisions").isArray())
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].decidedById").isNotEmpty());
    }

    // detail DRAFT заявки → 404
    @Test
    void detail_draftApp_returns404() throws Exception {
        UUID appId = createDraftApp(APPLICANT_ID);

        mockMvc.perform(get("/api/commission/applications/{id}", appId)
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isNotFound());
    }

    // detail несуществующей заявки → 404
    @Test
    void detail_missingApp_returns404() throws Exception {
        mockMvc.perform(get("/api/commission/applications/{id}", UUID.randomUUID())
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isNotFound());
    }

    // APPLICANT не может вызвать approve → 403
    @Test
    void applicant_cannotApprove_forbidden() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // approve заявки в нерешаемом статусе (ADDITIONAL_DOCUMENTS_REQUESTED) → 409
    @Test
    void nonDecidableStatus_returns409() throws Exception {
        UUID appId = createDraftApp(APPLICANT_ID);
        forceStatus(appId, ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED);

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isConflict());
    }

    // reason сохраняется в решении
    @Test
    void decision_reasonPreserved() throws Exception {
        UUID appId = createReviewableApp(APPLICANT_ID);

        MvcResult r = mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Approved: verified\"}"))
                .andExpect(status().isOk())
                .andReturn();

        var root = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(root.get("decisions").get(0).get("reason").asText())
                .isEqualTo("Approved: verified");
    }

    // --- helpers ---

    private void createUser(UUID id, String phone, UserRole role) {
        User u = new User();
        u.setId(id); u.setPhone(phone); u.setFullName("User " + role); u.setRole(role);
        userRepository.save(u);
    }

    private UUID createDraftApp(UUID applicantId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .with(user(applicantId.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"111111111111","region":"Almaty",
                                 "productionType":"GRAIN","landArea":50.0,"requestedAmount":1000000.0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createSubmittedApp(UUID applicantId) throws Exception {
        UUID id = createDraftApp(applicantId);
        forceSubmit(id);
        return id;
    }

    private UUID createReviewableApp(UUID applicantId) throws Exception {
        UUID id = createDraftApp(applicantId);
        makeReviewable(id);
        return id;
    }

    private void makeReviewable(UUID appId) {
        Application app = applicationRepository.findById(appId).orElseThrow();
        app.setSubmissionRevision(1);
        app.setStatus(ApplicationStatus.IN_REVIEW);
        applicationRepository.saveAndFlush(app);

        AiProcessingJob job = new AiProcessingJob();
        job.setOperationType(AiOperationType.SCORING);
        job.setApplication(app);
        job.setSubmissionRevision(1);
        job.setStatus(AiTaskStatus.COMPLETED);
        job.setMaxAttempts(3);
        job.setCompletedAt(Instant.now());
        job = aiJobRepository.saveAndFlush(job);

        ApplicationScore score = new ApplicationScore();
        score.setApplicationId(appId);
        score.setSubmissionRevision(1);
        score.setJob(job);
        score.setScore(new BigDecimal("90.00"));
        score.setRiskCategory("LOW");
        score.setRecommendedAmount(app.getRequestedAmount());
        score.setTopFactors("[]");
        score.setModelName("commission-test-model");
        score.setModelVersion("test-v1");
        score.setModelMetadata("{}");
        score.setCheckedAt(Instant.now());
        scoreRepository.saveAndFlush(score);
    }

    private void forceSubmit(UUID appId) {
        Application app = applicationRepository.findById(appId).orElseThrow();
        app.setSubmissionRevision(1);
        app.setStatus(ApplicationStatus.SUBMITTED);
        applicationRepository.save(app);
    }

    private void forceStatus(UUID appId, ApplicationStatus status) {
        Application app = applicationRepository.findById(appId).orElseThrow();
        app.setStatus(status);
        applicationRepository.save(app);
    }

    private UUID createSubmittedAppWith(UUID applicantId, String region, String amount) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .with(user(applicantId.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"111111111111","region":"%s",
                                 "productionType":"GRAIN","landArea":50.0,"requestedAmount":%s}
                                """.formatted(region, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        UUID id = UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
        forceStatus(id, ApplicationStatus.SUBMITTED);
        return id;
    }

    private UUID uploadDoc(UUID appId, UUID userId) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A});
        MvcResult r = mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(pdf)
                        .param("documentType", "OTHER")
                        .with(user(userId.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }
}
