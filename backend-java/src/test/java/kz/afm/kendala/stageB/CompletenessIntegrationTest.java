package kz.afm.kendala.stageB;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.UUID;
import kz.afm.kendala.ai.AiOrchestrationService;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class CompletenessIntegrationTest {

    static final UUID USER_ID  = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    static final UUID OTHER_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

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
    @MockBean AiOrchestrationService aiOrchestrationService;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        createUser(USER_ID, "+77011110000");
        createUser(OTHER_ID, "+77022220000");
    }

    // отсутствующие поля → complete=false
    @Test
    void completeness_missingFields() throws Exception {
        UUID appId = createEmptyApp();

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingFields").isArray())
                .andExpect(jsonPath("$.missingFields.length()").value(5));
    }

    // отсутствующие документы → missingDocuments содержит обязательные типы
    @Test
    void completeness_missingDocuments() throws Exception {
        UUID appId = createFilledApp();

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingDocuments").isArray())
                .andExpect(jsonPath("$.missingDocuments.length()").value(2));
    }

    // complete=true когда всё заполнено
    @Test
    void completeness_complete() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");
        uploadDoc(appId, "LAND_CERTIFICATE");

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.missingFields.length()").value(0))
                .andExpect(jsonPath("$.missingDocuments.length()").value(0));
    }

    // OTHER не заменяет обязательный документ
    @Test
    void completeness_otherDoesNotSatisfyRequired() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "OTHER");

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingDocuments.length()").value(2));
    }

    // документ другой заявки не учитывается
    @Test
    void completeness_otherAppDocNotCounted() throws Exception {
        UUID app1 = createFilledApp();
        UUID app2 = createFilledApp();
        uploadDoc(app2, "IIN_CERTIFICATE");
        uploadDoc(app2, "LAND_CERTIFICATE");

        mockMvc.perform(get("/api/applications/{id}/completeness", app1)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingDocuments.length()").value(2));
    }

    // incomplete submit → 422, статус остаётся DRAFT, history не создаётся
    @Test
    void submit_incomplete_returns422AndStatusStaysDraft() throws Exception {
        UUID appId = createFilledApp();
        long historyCountBefore = historyRepository.count();

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"))
                .andExpect(jsonPath("$.missingDocuments").isArray());

        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.DRAFT);
        assertThat(historyRepository.count()).isEqualTo(historyCountBefore);
    }

    // complete submit → 200 + статус SUBMITTED
    @Test
    void submit_complete_succeeds() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");
        uploadDoc(appId, "LAND_CERTIFICATE");

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.SUBMITTED);
    }

    // повторный submit запрещён
    @Test
    void submit_repeated_rejected() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");
        uploadDoc(appId, "LAND_CERTIFICATE");

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict());
    }

    // чужая заявка → 404
    @Test
    void completeness_foreignApp_returns404() throws Exception {
        UUID appId = createFilledApp();

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(OTHER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // один из двух обязательных документов загружен → 1 пропущен
    @Test
    void completeness_oneDocUploaded_oneStillMissing() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");

        mockMvc.perform(get("/api/applications/{id}/completeness", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.missingDocuments.length()").value(1))
                .andExpect(jsonPath("$.missingDocuments[0]").value("LAND_CERTIFICATE"));
    }

    // несуществующая заявка → 404
    @Test
    void completeness_unknownApp_returns404() throws Exception {
        mockMvc.perform(get("/api/applications/{id}/completeness", UUID.randomUUID())
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // полный submit создаёт ровно одну запись истории
    @Test
    void submit_complete_createsExactlyOneHistory() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");
        uploadDoc(appId, "LAND_CERTIFICATE");
        long historyBefore = historyRepository.count();

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk());

        assertThat(historyRepository.count()).isEqualTo(historyBefore + 1);
    }

    // ADDITIONAL_DOCUMENTS_REQUESTED без новых документов → submit запрещён (422)
    @Test
    void additionalDocs_incompleteApp_cannotResubmit() throws Exception {
        UUID appId = createFilledApp();

        kz.afm.kendala.application.entity.Application app =
                applicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED);
        applicationRepository.save(app);

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));

        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED);
    }

    // ADDITIONAL_DOCUMENTS_REQUESTED + загружены нужные документы → SUBMITTED
    @Test
    void additionalDocs_afterUpload_resubmitSucceeds() throws Exception {
        UUID appId = createFilledApp();
        uploadDoc(appId, "IIN_CERTIFICATE");
        uploadDoc(appId, "LAND_CERTIFICATE");

        kz.afm.kendala.application.entity.Application app =
                applicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.ADDITIONAL_DOCUMENTS_REQUESTED);
        applicationRepository.save(app);

        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.SUBMITTED);
    }

    // --- helpers ---

    private void createUser(UUID id, String phone) {
        User u = new User();
        u.setId(id); u.setPhone(phone); u.setFullName("Test"); u.setRole(UserRole.APPLICANT);
        userRepository.save(u);
    }

    private UUID createEmptyApp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .with(user(USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createFilledApp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .with(user(USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"111111111111","region":"Almaty",
                                 "productionType":"GRAIN","landArea":50.0,"requestedAmount":1000000.0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private void uploadDoc(UUID appId, String docType) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A});
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(pdf)
                        .param("documentType", docType)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated());
    }
}
