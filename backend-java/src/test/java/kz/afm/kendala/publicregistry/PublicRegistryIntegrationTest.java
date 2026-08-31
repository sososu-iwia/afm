package kz.afm.kendala.publicregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@TestPropertySource(properties = "app.documents.required-types=")
class PublicRegistryIntegrationTest {

    private static final UUID APPLICANT_ID =
            UUID.fromString("92000000-0000-0000-0000-000000000001");
    private static final UUID APPROVED_A =
            UUID.fromString("92000000-0000-0000-0000-000000000011");
    private static final UUID APPROVED_B =
            UUID.fromString("92000000-0000-0000-0000-000000000012");

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
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        jdbc.update(
                """
                INSERT INTO users(id, phone, full_name, role, active, verified_at, email, account_status)
                VALUES (?, '+77001112233', 'Secret Applicant Name', 'APPLICANT',
                        true, now(), 'secret@example.org', 'ACTIVE')
                """,
                APPLICANT_ID
        );
        insertApplication(
                APPROVED_A,
                "PUBLIC-APPROVED-A",
                "APPROVED",
                "Akmola",
                "GRAIN",
                "CROP_PRODUCTION",
                "PEASANT_FARM",
                "500.00",
                "2026-01-05T10:00:00Z",
                "991122334455"
        );
        insertApplication(
                APPROVED_B,
                "PUBLIC-APPROVED-B",
                "APPROVED",
                "Almaty",
                "MILK",
                "LIVESTOCK_PRODUCTION",
                "COOPERATIVE",
                "100.00",
                "2026-02-01T12:00:00Z",
                "880011223344"
        );
        insertApplication(
                UUID.randomUUID(), "PRIVATE-REJECTED", "REJECTED", "Akmola", "GRAIN",
                "CROP_PRODUCTION", "LEGAL_ENTITY", null, "2026-01-10T00:00:00Z",
                "770011223344"
        );
        insertApplication(
                UUID.randomUUID(), "PRIVATE-DRAFT", "DRAFT", "Akmola", "GRAIN",
                "CROP_PRODUCTION", "INDIVIDUAL_ENTREPRENEUR", null, null,
                "660011223344"
        );
        insertApplication(
                UUID.randomUUID(), "PRIVATE-SUBMITTED", "SUBMITTED", "Almaty", "MILK",
                "LIVESTOCK_PRODUCTION", "OTHER", null, null, "550011223344"
        );
        insertApplication(
                UUID.randomUUID(), "PRIVATE-WITHDRAWN", "WITHDRAWN", "Almaty", "MILK",
                "LIVESTOCK_PRODUCTION", "OTHER", null, null, "440011223344"
        );
        insertScore(APPROVED_A, "LOW");
        insertDecision(APPROVED_A, "Internal approval reason must stay private");
    }

    @Test
    void anonymousResponseContainsOnlyApprovedAnonymizedProjection() throws Exception {
        String body = mockMvc.perform(get("/api/public/approved-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].applicationNumber")
                        .value("PUBLIC-APPROVED-B"))
                .andExpect(jsonPath("$.content[1].applicationNumber")
                        .value("PUBLIC-APPROVED-A"))
                .andExpect(jsonPath("$.content[1].activityType")
                        .value("CROP_PRODUCTION"))
                .andExpect(jsonPath("$.content[1].approvedAmount").value(500.0))
                .andExpect(jsonPath("$.content[1].applicantCategory")
                        .value("PEASANT_FARM"))
                .andExpect(jsonPath("$.content[1].scoringCategory").value("LOW"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain(
                        "PRIVATE-REJECTED",
                        "PRIVATE-DRAFT",
                        "PRIVATE-SUBMITTED",
                        "PRIVATE-WITHDRAWN",
                        "Secret Applicant Name",
                        "+77001112233",
                        "secret@example.org",
                        "991122334455",
                        "Internal approval reason must stay private",
                        APPLICANT_ID.toString()
                );

        JsonNode item = objectMapper.readTree(body).path("content").get(0);
        assertThat(item.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "applicationNumber",
                        "region",
                        "activityType",
                        "productionType",
                        "approvedAmount",
                        "decisionDate",
                        "applicantCategory",
                        "scoringCategory"
                ));
    }

    @Test
    void filtersSortingAndPaginationAreAppliedInDatabase() throws Exception {
        mockMvc.perform(get("/api/public/approved-applications")
                        .param("region", "Akmola")
                        .param("productionType", "GRAIN")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-01-31")
                        .param("minAmount", "200")
                        .param("maxAmount", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].applicationNumber")
                        .value("PUBLIC-APPROVED-A"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/public/approved-applications")
                        .param("page", "2")
                        .param("size", "1")
                        .param("sortBy", "approvedAmount")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].applicationNumber")
                        .value("PUBLIC-APPROVED-A"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void invalidRangeIsRejectedAndNonGetIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/public/approved-applications")
                        .param("minAmount", "600")
                        .param("maxAmount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));

        mockMvc.perform(post("/api/public/approved-applications"))
                .andExpect(status().isUnauthorized());
    }

    private void insertApplication(
            UUID id,
            String number,
            String status,
            String region,
            String productionType,
            String activityType,
            String applicantCategory,
            String approvedAmount,
            String decisionAt,
            String iinOrBin
    ) {
        jdbc.update(
                """
                INSERT INTO applications(
                    id, application_number, applicant_id, status, iin_or_bin,
                    region, production_type, activity_type, applicant_category,
                    land_area, requested_amount, approved_amount, decision_at,
                    created_at, updated_at, version, is_public
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 10, 600,
                        ?::numeric, ?, '2026-01-01T00:00:00Z',
                        COALESCE(?, '2026-01-01T00:00:00Z'::timestamptz), 0, ?)
                """,
                id,
                number,
                APPLICANT_ID,
                status,
                iinOrBin,
                region,
                productionType,
                activityType,
                applicantCategory,
                approvedAmount,
                timestamp(decisionAt),
                timestamp(decisionAt),
                "APPROVED".equals(status)
        );
    }

    private void insertScore(UUID applicationId, String riskCategory) {
        UUID jobId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO ai_processing_jobs(
                    id, operation_type, application_id, status, attempt_count,
                    max_attempts, created_at, completed_at, updated_at, version, submission_revision
                )
                VALUES (?, 'SCORING', ?, 'COMPLETED', 1, 3, now(), now(), now(), 0, 1)
                """,
                jobId, applicationId
        );
        jdbc.update(
                """
                INSERT INTO application_scores(
                    application_id, job_id, score, risk_category,
                    top_factors, llm_summary, checked_at, submission_revision
                )
                VALUES (?, ?, 90, ?, '["private factor"]'::jsonb,
                        'private scoring summary', now(), 1)
                """,
                applicationId, jobId, riskCategory
        );
    }

    private void insertDecision(UUID applicationId, String reason) {
        jdbc.update(
                """
                INSERT INTO decisions(id, application_id, decided_by, decision_type, reason)
                VALUES (?, ?, ?, 'APPROVED', ?)
                """,
                UUID.randomUUID(), applicationId, APPLICANT_ID, reason
        );
    }

    private Timestamp timestamp(String value) {
        return value == null ? null : Timestamp.from(Instant.parse(value));
    }
}
