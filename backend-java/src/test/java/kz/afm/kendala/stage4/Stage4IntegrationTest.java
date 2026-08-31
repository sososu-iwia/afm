package kz.afm.kendala.stage4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.DocumentRepository;
import kz.afm.kendala.application.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "app.documents.required-types=")
class Stage4IntegrationTest {

    static final UUID TEST_USER_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @TempDir
    static Path tempStorageDir;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("app.storage.local.path", () -> tempStorageDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApplicationStatusHistoryRepository historyRepository;
    @SpyBean DocumentRepository documentRepository;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        ensureUser(TEST_USER_ID,  "+77001111111", "Test User");
        ensureUser(OTHER_USER_ID, "+77002222222", "Other User");
    }

    @AfterEach
    void resetMocks() {
        reset(documentRepository);
    }

    // #1 Загрузка PDF
    @Test
    void upload_pdf_createsDocument() throws Exception {
        UUID appId = createDraftApp();
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.documentType").value("OTHER"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.originalFileName").value("document.pdf"));
    }

    // #2 Загрузка JPEG
    @Test
    void upload_jpeg_createsDocument() throws Exception {
        UUID appId = createDraftApp();
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validJpeg())
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"));
    }

    // #3 Загрузка PNG
    @Test
    void upload_png_createsDocument() throws Exception {
        UUID appId = createDraftApp();
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPng())
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    // #4 Пустой файл отклоняется
    @Test
    void upload_emptyFile_rejected() throws Exception {
        UUID appId = createDraftApp();
        MockMultipartFile empty = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(empty)
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"));
    }

    // #5 Превышение размера отклоняется (max-size-bytes=1024 в тест-конфиге)
    @Test
    void upload_tooLarge_rejected() throws Exception {
        UUID appId = createDraftApp();
        byte[] large = new byte[1025];
        large[0] = 0x25; large[1] = 0x50; large[2] = 0x44; large[3] = 0x46; large[4] = 0x2D;
        MockMultipartFile bigFile = new MockMultipartFile("file", "big.pdf", "application/pdf", large);
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(bigFile)
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    // #6 Неправильное расширение отклоняется
    @Test
    void upload_wrongExtension_rejected() throws Exception {
        UUID appId = createDraftApp();
        MockMultipartFile exe = new MockMultipartFile("file", "malware.exe", "application/pdf", pdfBytes());
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(exe)
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
    }

    // #7 Неправильный MIME отклоняется
    @Test
    void upload_wrongMime_rejected() throws Exception {
        UUID appId = createDraftApp();
        MockMultipartFile wrongMime = new MockMultipartFile("file", "document.pdf", "image/jpeg", pdfBytes());
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(wrongMime)
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONTENT_TYPE"));
    }

    // #8 Сигнатура не совпадает — отклоняется
    @Test
    void upload_invalidSignature_rejected() throws Exception {
        UUID appId = createDraftApp();
        // PDF extension + PDF MIME but JPEG magic bytes
        MockMultipartFile mismatch = new MockMultipartFile("file", "document.pdf", "application/pdf", jpegBytes());
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(mismatch)
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_CONTENT"));
    }

    // #9 Нельзя загрузить в чужую заявку
    @Test
    void upload_foreignApplication_returns404() throws Exception {
        UUID appId = createDraftApp(); // принадлежит TEST_USER_ID
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // #10 Загрузка в SUBMITTED запрещена
    @Test
    void upload_submittedApp_rejected() throws Exception {
        UUID appId = createDraftApp();
        submitApp(appId);
        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // #11 Список документов своей заявки
    @Test
    void list_ownDocuments_returnsAll() throws Exception {
        UUID appId = createDraftApp();
        uploadDoc(appId, TEST_USER_ID, "IIN_CERTIFICATE");
        uploadDoc(appId, TEST_USER_ID, "LAND_CERTIFICATE");

        mockMvc.perform(get("/api/applications/{id}/documents", appId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // #12 Чужой список недоступен
    @Test
    void list_foreignApplication_returns404() throws Exception {
        UUID appId = createDraftApp();
        mockMvc.perform(get("/api/applications/{id}/documents", appId)
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // #13 Скачивание возвращает правильное содержимое и headers
    @Test
    void download_returnsCorrectContentAndHeaders() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId, TEST_USER_ID);

        MvcResult result = mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).startsWith(pdfBytes()[0]);
    }

    // #14 Чужое скачивание недоступно
    @Test
    void download_foreignDocument_returns404() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId, TEST_USER_ID);

        mockMvc.perform(get("/api/documents/{id}/download", docId)
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // #15 Удаление из DRAFT
    @Test
    void delete_draftDocument_succeeds() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId, TEST_USER_ID);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/documents/{id}", docId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());
    }

    // #16 Удаление после SUBMITTED запрещено
    @Test
    void delete_submittedDocument_rejected() throws Exception {
        UUID appId = createDraftApp();
        UUID docId = uploadDoc(appId, TEST_USER_ID);
        submitApp(appId);

        mockMvc.perform(delete("/api/documents/{id}", docId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    // #17 При ошибке сохранения в БД загруженный файл удаляется
    @Test
    void upload_dbFailureCleansUpStorage() throws Exception {
        UUID appId = createDraftApp();
        long filesBefore = countStorageFiles();

        doThrow(new RuntimeException("Simulated DB failure"))
                .when(documentRepository).saveAndFlush(any(Document.class));

        mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", "OTHER")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isInternalServerError());

        assertThat(countStorageFiles()).isEqualTo(filesBefore);
    }

    // --- Helpers ---

    private void ensureUser(UUID id, String phone, String name) {
        if (!userRepository.existsById(id)) {
            User u = new User();
            u.setId(id);
            u.setPhone(phone);
            u.setFullName(name);
            u.setRole(UserRole.APPLICANT);
            userRepository.save(u);
        }
    }

    private UUID createDraftApp() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/applications")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iinOrBin":"123456789012","region":"Akmola",
                                 "productionType":"GRAIN","landArea":100.0,"requestedAmount":5000000.0}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private void submitApp(UUID appId) throws Exception {
        mockMvc.perform(post("/api/applications/{id}/submit", appId)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk());
    }

    private UUID uploadDoc(UUID appId, UUID userId) throws Exception {
        return uploadDoc(appId, userId, "OTHER");
    }

    private UUID uploadDoc(UUID appId, UUID userId, String documentType) throws Exception {
        MvcResult r = mockMvc.perform(multipart("/api/applications/{id}/documents", appId)
                        .file(validPdf())
                        .param("documentType", documentType)
                        .with(user(userId.toString()).roles("APPLICANT")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private long countStorageFiles() throws IOException {
        if (!Files.exists(tempStorageDir)) return 0;
        try (var stream = Files.list(tempStorageDir)) {
            return stream.count();
        }
    }

    static byte[] pdfBytes() {
        return new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A};
    }

    private static MockMultipartFile validPdf() {
        return new MockMultipartFile("file", "document.pdf", "application/pdf", pdfBytes());
    }

    private static MockMultipartFile validJpeg() {
        byte[] b = {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49};
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", b);
    }

    private static MockMultipartFile validPng() {
        byte[] b = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
        return new MockMultipartFile("file", "image.png", "image/png", b);
    }

    static byte[] jpegBytes() {
        return new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49};
    }
}
