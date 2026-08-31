package kz.afm.kendala.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class ApplicationIntegrationTest {

    static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApplicationStatusHistoryRepository historyRepository;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        ensureUser(TEST_USER_ID, "+77000000001", "Test Applicant");
        ensureUser(OTHER_USER_ID, "+77000000002", "Other Applicant");
    }

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

    @Test
    void createsDraftApplicationAndUniqueNumber() throws Exception {
        JsonNode first = createApplication(validCreatePayload());
        JsonNode second = createApplication(validCreatePayload());

        assertThat(first.get("status").asText()).isEqualTo("DRAFT");
        assertThat(first.get("applicationNumber").asText()).isNotBlank();
        assertThat(second.get("applicationNumber").asText())
                .isNotEqualTo(first.get("applicationNumber").asText());
    }

    @Test
    void updatesDraftApplication() throws Exception {
        UUID id = UUID.fromString(createApplication("{}").get("id").asText());

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("Akmola"))
                .andExpect(jsonPath("$.requestedAmount").value(15000000.00));
    }

    @Test
    void submittedApplicationCannotBeEdited() throws Exception {
        UUID id = UUID.fromString(createApplication(validCreatePayload()).get("id").asText());
        mockMvc.perform(post("/api/applications/{id}/submit", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void applicationWithoutRequiredDataCannotBeSubmitted() throws Exception {
        UUID id = UUID.fromString(createApplication("{}").get("id").asText());

        mockMvc.perform(post("/api/applications/{id}/submit", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_INCOMPLETE"));
    }

    @Test
    void submitCreatesStatusHistoryAndDuplicateSubmitIsRejected() throws Exception {
        UUID id = UUID.fromString(createApplication(validCreatePayload()).get("id").asText());

        mockMvc.perform(post("/api/applications/{id}/submit", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/applications/{id}/status-history", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].previousStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].newStatus").value("SUBMITTED"));

        mockMvc.perform(post("/api/applications/{id}/submit", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void applicantCannotAccessForeignApplication() throws Exception {
        UUID id = UUID.fromString(createApplication(validCreatePayload()).get("id").asText());

        mockMvc.perform(get("/api/applications/{id}", id)
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/applications/{id}", id)
                        .with(user(OTHER_USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isNotFound());
    }

    @Test
    void withdrawCreatesStatusHistory() throws Exception {
        UUID id = UUID.fromString(createApplication(validCreatePayload()).get("id").asText());

        mockMvc.perform(post("/api/applications/{id}/withdraw", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        mockMvc.perform(get("/api/applications/{id}/status-history", id)
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].previousStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].newStatus").value("WITHDRAWN"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/applications/{id}", UUID.randomUUID())
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void listApplicationsIsPaginated() throws Exception {
        createApplication(validCreatePayload());

        mockMvc.perform(get("/api/applications?page=1&size=20")
                .with(user(TEST_USER_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private JsonNode createApplication(String payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/applications")
                        .with(user(TEST_USER_ID.toString()).roles("APPLICANT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String validCreatePayload() {
        return """
                {
                  "iinOrBin": "123456789012",
                  "region": "Akmola",
                  "productionType": "GRAIN",
                  "landArea": 500.00,
                  "requestedAmount": 15000000.00
                }
                """;
    }
}
