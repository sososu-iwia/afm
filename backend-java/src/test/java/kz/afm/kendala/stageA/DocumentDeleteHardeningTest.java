package kz.afm.kendala.stageA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.common.exception.StorageException;
import kz.afm.kendala.filestore.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
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
class DocumentDeleteHardeningTest {

    static final UUID USER_ID  = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID OTHER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @TempDir
    static Path tempDir;

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
    @SpyBean StorageService storageService;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        User u = new User();
        u.setId(USER_ID);
        u.setPhone("+77009990000");
        u.setFullName("Delete Test User");
        u.setRole(UserRole.APPLICANT);
        userRepository.save(u);

        User other = new User();
        other.setId(OTHER_ID);
        other.setPhone("+77009990001");
        other.setFullName("Other User");
        other.setRole(UserRole.APPLICANT);
        userRepository.save(other);
    }

    @AfterEach
    void resetSpies() {
        reset(storageService);
    }

    // storage failure → DB metadata остаётся
    @Test
    void delete_storageFailure_metadataPreserved() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);

        doThrow(new StorageException("storage down")).when(storageService).delete(anyString());

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isServiceUnavailable());

        assertThat(documentRepository.existsById(docId)).isTrue();
    }

    // файл уже отсутствует → metadata удаляется (idempotent)
    @Test
    void delete_fileAlreadyAbsent_metadataDeleted() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);

        // remove physical file
        try (var stream = Files.list(tempDir)) {
            stream.forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNoContent());

        assertThat(documentRepository.existsById(docId)).isFalse();
    }

    // успешное удаление → файл и metadata удалены
    @Test
    void delete_success_fileAndMetadataRemoved() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);
        long filesBefore = countFiles();

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNoContent());

        assertThat(documentRepository.existsById(docId)).isFalse();
        assertThat(countFiles()).isLessThan(filesBefore);
    }

    // повторный DELETE → 404
    @Test
    void delete_repeated_returns404() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // чужой пользователь → 404 (masking)
    @Test
    void delete_foreignUser_returns404() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(OTHER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());

        assertThat(documentRepository.existsById(docId)).isTrue();
    }

    // документ заявки в статусе SUBMITTED → 409
    @Test
    void delete_submittedApp_rejected() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId);

        Application app = applicationRepository.findById(appId).orElseThrow();
        app.setStatus(ApplicationStatus.SUBMITTED);
        applicationRepository.save(app);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict());

        assertThat(documentRepository.existsById(docId)).isTrue();
    }

    // --- helpers ---

    private UUID createDraftApp() throws Exception {
        MvcResult r = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/applications")
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

    private UUID uploadDoc(UUID appId) throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A});
        MvcResult r = mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(pdf)
                        .param("documentType", "OTHER")
                        .with(user(USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private long countFiles() throws Exception {
        if (!Files.exists(tempDir)) return 0;
        try (var s = Files.list(tempDir)) { return s.count(); }
    }
}
