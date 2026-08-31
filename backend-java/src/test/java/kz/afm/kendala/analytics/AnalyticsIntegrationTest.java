package kz.afm.kendala.analytics;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.math.BigDecimal;
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
class AnalyticsIntegrationTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID APPLICANT_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000002");
    private static final UUID APPROVED_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000011");
    private static final UUID REJECTED_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000012");
    private static final UUID DRAFT_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000013");

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

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        insertUser(ADMIN_ID, "+77090000001", "ADMIN");
        insertUser(APPLICANT_ID, "+77090000002", "APPLICANT");
        insertApplication(
                APPROVED_ID,
                "AN-APPROVED",
                "APPROVED",
                "Akmola",
                "GRAIN",
                "100.00",
                "80.00",
                "2026-01-01T10:00:00Z",
                "2026-01-03T12:00:00Z"
        );
        insertApplication(
                REJECTED_ID,
                "AN-REJECTED",
                "REJECTED",
                "Almaty",
                "MILK",
                "200.00",
                null,
                "2026-01-02T00:00:00Z",
                "2026-01-02T10:00:00Z"
        );
        insertApplication(
                DRAFT_ID,
                "AN-DRAFT",
                "DRAFT",
                "Akmola",
                "GRAIN",
                "300.00",
                null,
                "2026-02-01T00:00:00Z",
                null
        );
        insertDecision(APPROVED_ID, "ADDITIONAL_DOCUMENTS_REQUESTED", "2026-01-02T00:00:00Z");
        insertDecision(APPROVED_ID, "APPROVED", "2026-01-03T12:00:00Z");
        insertDecision(REJECTED_ID, "REJECTED", "2026-01-02T10:00:00Z");
        insertCompletedScore(APPROVED_ID);
        insertFailedAiTask(REJECTED_ID);
    }

    @Test
    void summaryUsesDatabaseAggregatesAndFilters() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-01-31")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.APPROVED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.REJECTED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.DRAFT").value(0))
                .andExpect(jsonPath("$.totalRequestedAmount").value(300.0))
                .andExpect(jsonPath("$.totalApprovedAmount").value(80.0))
                .andExpect(jsonPath("$.averageRequestedAmount").value(150.0))
                .andExpect(jsonPath(
                        "$.averageProcessingHours",
                        comparesEqualTo(new BigDecimal("30")),
                        BigDecimal.class
                ))
                .andExpect(jsonPath("$.approvedDecisions").value(1))
                .andExpect(jsonPath("$.rejectedDecisions").value(1))
                .andExpect(jsonPath("$.additionalDocumentsRequestedDecisions").value(1))
                .andExpect(jsonPath("$.completedAiScoringApplications").value(1))
                .andExpect(jsonPath("$.failedAiTasks").value(1));

        mockMvc.perform(get("/api/analytics/summary")
                        .param("region", "Akmola")
                        .param("productionType", "GRAIN")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(2))
                .andExpect(jsonPath("$.applicationsByStatus.APPROVED").value(1))
                .andExpect(jsonPath("$.applicationsByStatus.DRAFT").value(1));
    }

    @Test
    void groupedEndpointsUseUnifiedPaginationAndUtcDates() throws Exception {
        mockMvc.perform(get("/api/analytics/applications-by-region")
                        .param("page", "1")
                        .param("size", "1")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].region").value("Akmola"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/analytics/application-trend")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-01-31")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].date").value("2026-01-01"))
                .andExpect(jsonPath("$.content[1].date").value("2026-01-02"));

        mockMvc.perform(get("/api/analytics/processing-time")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].decisionDate").value("2026-01-02"))
                .andExpect(jsonPath(
                        "$.content[0].averageProcessingHours",
                        comparesEqualTo(new BigDecimal("10")),
                        BigDecimal.class
                ));
    }

    @Test
    void accessIsRestrictedAndInvalidDateRangeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/analytics/summary")
                        .with(user(APPLICANT_ID.toString()).roles("APPLICANT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/analytics/summary")
                        .param("dateFrom", "2026-02-01")
                        .param("dateTo", "2026-01-01")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    }

    @Test
    void emptyResultReturnsZerosAndEmptyPage() throws Exception {
        mockMvc.perform(get("/api/analytics/summary")
                        .param("region", "NotFound")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalApplications").value(0))
                .andExpect(jsonPath("$.totalRequestedAmount").value(0))
                .andExpect(jsonPath("$.averageProcessingHours").value(0));

        mockMvc.perform(get("/api/analytics/applications-by-status")
                        .param("region", "NotFound")
                        .with(user(ADMIN_ID.toString()).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    private void insertUser(UUID id, String phone, String role) {
        jdbc.update(
                """
                INSERT INTO users(id, phone, full_name, role, active, verified_at, account_status)
                VALUES (?, ?, ?, ?, true, now(), 'ACTIVE')
                """,
                id, phone, "Analytics " + role, role
        );
    }

    private void insertApplication(
            UUID id,
            String number,
            String status,
            String region,
            String productionType,
            String requestedAmount,
            String approvedAmount,
            String createdAt,
            String decisionAt
    ) {
        jdbc.update(
                """
                INSERT INTO applications(
                    id, application_number, applicant_id, status, iin_or_bin,
                    region, production_type, land_area, requested_amount,
                    approved_amount, decision_at, created_at, updated_at, version
                )
                VALUES (?, ?, ?, ?, '123456789012', ?, ?, 10, ?::numeric,
                        ?::numeric, ?, ?, ?, 0)
                """,
                id,
                number,
                APPLICANT_ID,
                status,
                region,
                productionType,
                requestedAmount,
                approvedAmount,
                timestamp(decisionAt),
                timestamp(createdAt),
                timestamp(decisionAt == null ? createdAt : decisionAt)
        );
    }

    private void insertDecision(UUID applicationId, String type, String createdAt) {
        jdbc.update(
                """
                INSERT INTO decisions(id, application_id, decided_by, decision_type, reason, created_at)
                VALUES (?, ?, ?, ?, 'test decision', ?)
                """,
                UUID.randomUUID(),
                applicationId,
                ADMIN_ID,
                type,
                timestamp(createdAt)
        );
    }

    private void insertCompletedScore(UUID applicationId) {
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
                    application_id, job_id, score, risk_category, recommended_amount,
                    top_factors, llm_summary, checked_at, submission_revision
                )
                VALUES (?, ?, 88.5, 'LOW', 80, '[]'::jsonb, 'test', now(), 1)
                """,
                applicationId, jobId
        );
    }

    private void insertFailedAiTask(UUID applicationId) {
        jdbc.update(
                """
                INSERT INTO ai_processing_jobs(
                    id, operation_type, application_id, status, attempt_count,
                    max_attempts, error_code, created_at, updated_at, version, submission_revision
                )
                VALUES (?, 'SCORING', ?, 'FAILED', 3, 3, 'UPSTREAM', now(), now(), 0, 1)
                """,
                UUID.randomUUID(), applicationId
        );
    }

    private Timestamp timestamp(String value) {
        return value == null ? null : Timestamp.from(Instant.parse(value));
    }
}
