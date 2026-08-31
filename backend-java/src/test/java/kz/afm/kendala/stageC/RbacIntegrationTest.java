package kz.afm.kendala.stageC;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
@TestPropertySource(properties = "app.documents.required-types=")
class RbacIntegrationTest {

    static final UUID APPLICANT_ID = UUID.fromString("bb000000-0000-0000-0000-000000000001");
    static final UUID OTHER_APP_ID = UUID.fromString("bb000000-0000-0000-0000-000000000002");
    static final UUID MEMBER_ID    = UUID.fromString("bb000000-0000-0000-0000-000000000003");
    static final UUID CHAIRMAN_ID  = UUID.fromString("bb000000-0000-0000-0000-000000000004");
    static final UUID SECRETARY_ID = UUID.fromString("bb000000-0000-0000-0000-000000000005");
    static final UUID MANAGER_ID   = UUID.fromString("bb000000-0000-0000-0000-000000000006");

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

    @BeforeEach
    void setUp() {
        decisionRepository.deleteAll();
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        createUser(APPLICANT_ID, "+77080000001", UserRole.APPLICANT);
        createUser(OTHER_APP_ID, "+77080000002", UserRole.APPLICANT);
        createUser(MEMBER_ID,    "+77080000003", UserRole.COMMISSION_MEMBER);
        createUser(CHAIRMAN_ID,  "+77080000004", UserRole.CHAIRMAN);
        createUser(SECRETARY_ID, "+77080000005", UserRole.SECRETARY);
        createUser(MANAGER_ID,   "+77080000006", UserRole.MANAGER);
    }

    // --- Applicant ownership enforcement ---

    // APPLICANT не отправляет чужую заявку (#9)
    @Test
    void applicant_cannotSubmitForeignApplication() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(OTHER_APP_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // APPLICANT не отзывает чужую заявку (#10)
    @Test
    void applicant_cannotWithdrawForeignApplication() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        mockMvc.perform(post("/api/applications/{id}/withdraw", appId)
                        .with(user(OTHER_APP_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // APPLICANT не загружает документ в чужую заявку (#13)
    @Test
    void applicant_cannotUploadToForeignApp() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(OTHER_APP_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // --- Commission roles blocked from applicant write endpoints ---

    // служебная роль не создаёт заявку (#11)
    @Test
    void commissionRole_cannotCreateApplication() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isForbidden());
    }

    // служебная роль не удаляет документ заявителя (#19)
    @Test
    void commissionRole_cannotDeleteDocument() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        UUID docId = uploadDoc(appId);
        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isForbidden());
    }

    // --- Commission read access ---

    // SECRETARY видит список комиссии (#23)
    @Test
    void secretary_canAccessCommissionList() throws Exception {
        mockMvc.perform(get("/api/commission/applications")
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // SECRETARY видит карточку заявки (#23)
    @Test
    void secretary_canAccessCommissionDetail() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(get("/api/commission/applications/{id}", appId)
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appId.toString()));
    }

    // SECRETARY скачивает документ доступной заявки (#17)
    @Test
    void secretary_canDownloadDocument() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        UUID docId = uploadDoc(appId);
        forceStatus(appId, ApplicationStatus.SUBMITTED);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isOk());
    }

    // CHAIRMAN скачивает документ доступной заявки (#18)
    @Test
    void chairman_canDownloadDocument() throws Exception {
        UUID appId = createApp(APPLICANT_ID);
        UUID docId = uploadDoc(appId);
        forceStatus(appId, ApplicationStatus.SUBMITTED);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN")))
                .andExpect(status().isOk());
    }

    // --- Commission blocked from DRAFT documents (#20) ---

    @Test
    void commission_cannotAccessDraftAppDocument() throws Exception {
        UUID appId = createApp(APPLICANT_ID); // остаётся DRAFT
        UUID docId = uploadDoc(appId);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isNotFound());
    }

    // --- Commission decision restrictions ---

    // COMMISSION_MEMBER не отказывает (#26)
    @Test
    void commissionMember_cannotReject() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/reject", appId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user(MEMBER_ID.toString()).roles("COMMISSION_MEMBER")))
                .andExpect(status().isForbidden());
    }

    // SECRETARY не одобряет (#27)
    @Test
    void secretary_cannotApprove() throws Exception {
        UUID appId = createSubmittedApp(APPLICANT_ID);
        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(user(SECRETARY_ID.toString()).roles("SECRETARY")))
                .andExpect(status().isForbidden());
    }

    // MANAGER не получает доступ к комиссии (#30)
    @Test
    void manager_cannotAccessCommission() throws Exception {
        mockMvc.perform(get("/api/commission/applications")
                        .with(user(MANAGER_ID.toString()).roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    // --- JWT security (#34, #33) ---

    // повреждённый JWT → 401
    @Test
    void corruptedJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // X-User-Id без JWT не даёт доступ (#33)
    @Test
    void xUserIdHeader_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get("/api/applications")
                        .header("X-User-Id", APPLICANT_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private void createUser(UUID id, String phone, UserRole role) {
        User u = new User();
        u.setId(id); u.setPhone(phone); u.setFullName("User " + role); u.setRole(role);
        userRepository.save(u);
    }

    private UUID createApp(UUID applicantId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"111111111111","region":"Almaty",
                                 "productionType":"GRAIN","landArea":50.0,"requestedAmount":1000000.0}
                                """)
                        .with(user(applicantId.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID createSubmittedApp(UUID applicantId) throws Exception {
        UUID id = createApp(applicantId);
        forceStatus(id, ApplicationStatus.SUBMITTED);
        return id;
    }

    private void forceStatus(UUID appId, ApplicationStatus status) {
        Application app = applicationRepository.findById(appId).orElseThrow();
        app.setStatus(status);
        applicationRepository.save(app);
    }

    private UUID uploadDoc(UUID appId) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private static MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A});
    }
}
