package kz.afm.kendala.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DatabasePerformanceIntegrationTest {

    private static final UUID APPLICANT_ID =
            UUID.fromString("ad000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_ID =
            UUID.fromString("ad000000-0000-0000-0000-000000000002");

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
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        insertUser(APPLICANT_ID, "+77070003001", "APPLICANT");
        insertUser(ADMIN_ID, "+77070003002", "ADMIN");
        jdbc.update("""
                INSERT INTO applications(
                    id, application_number, applicant_id, status, iin_or_bin,
                    region, production_type, land_area, requested_amount,
                    approved_amount, decision_at, created_at, updated_at, version
                )
                SELECT gen_random_uuid(),
                       'PERF-' || lpad(series::text, 6, '0'),
                       ?,
                       CASE WHEN series % 2 = 0 THEN 'APPROVED' ELSE 'SUBMITTED' END,
                       '123456789012',
                       CASE WHEN series % 3 = 0 THEN 'Akmola' ELSE 'Almaty' END,
                       'GRAIN',
                       100,
                       100000 + series,
                       CASE WHEN series % 2 = 0 THEN 90000 + series ELSE NULL END,
                       CASE WHEN series % 2 = 0 THEN now() ELSE NULL END,
                       TIMESTAMPTZ '2026-01-01 00:00:00+00',
                       TIMESTAMPTZ '2026-01-01 00:00:00+00',
                       0
                FROM generate_series(1, 500) AS series
                """, APPLICANT_ID);
        jdbc.execute("ANALYZE applications");
    }

    @Test
    void explainAnalyzeUsesIndexesForApplicantAndPublicQueries() {
        jdbc.execute("SET enable_seqscan = off");
        try {
            String applicantPlan = explain("""
                    SELECT id
                    FROM applications
                    WHERE applicant_id = '%s'
                    ORDER BY created_at DESC, id
                    LIMIT 20
                    """.formatted(APPLICANT_ID));
            String publicPlan = explain("""
                    SELECT application_number
                    FROM applications
                    WHERE status = 'APPROVED'
                    ORDER BY COALESCE(decision_at, updated_at) DESC, application_number
                    LIMIT 20
                    """);

            assertThat(applicantPlan).contains("idx_applications_applicant_created");
            assertThat(publicPlan).contains("idx_applications_public_decision");
            assertThat(applicantPlan).contains("actual time");
            assertThat(publicPlan).contains("actual time");
        } finally {
            jdbc.execute("RESET enable_seqscan");
        }
    }

    @Test
    void hardeningIndexesExistForAuditedQueryPaths() {
        List<String> names = jdbc.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                """, String.class);

        assertThat(names).contains(
                "idx_applications_applicant_created",
                "idx_applications_commission_queue",
                "idx_applications_commission_filters",
                "idx_applications_public_decision",
                "idx_documents_application_created",
                "idx_status_history_application_created",
                "idx_decisions_application_created",
                "idx_audit_logs_occurred",
                "idx_notification_outbox_due",
                "idx_ai_jobs_due"
        );
    }

    @Test
    void applicantAndCommissionPaginationHaveNoDuplicatesOnTiedSortValues() throws Exception {
        Set<String> applicantIds = new HashSet<>();
        Set<String> commissionIds = new HashSet<>();

        for (int page = 1; page <= 5; page++) {
            JsonNode applicantPage = response(
                    "/api/applications?page=" + page + "&size=100",
                    APPLICANT_ID,
                    "APPLICANT"
            );
            JsonNode commissionPage = response(
                    "/api/commission/applications?page=" + page
                            + "&size=100&sortBy=createdAt&sortDir=desc",
                    ADMIN_ID,
                    "ADMIN"
            );
            applicantPage.path("content").forEach(item ->
                    assertThat(applicantIds.add(item.path("id").asText())).isTrue());
            commissionPage.path("content").forEach(item ->
                    assertThat(commissionIds.add(item.path("id").asText())).isTrue());
        }

        assertThat(applicantIds).hasSize(500);
        assertThat(commissionIds).hasSize(500);
    }

    private JsonNode response(String path, UUID actorId, String role) throws Exception {
        String body = mockMvc.perform(get(path)
                        .with(user(actorId.toString()).roles(role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body);
    }

    private String explain(String query) {
        return String.join("\n", jdbc.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + query,
                String.class
        ));
    }

    private void insertUser(UUID id, String phone, String role) {
        jdbc.update("""
                INSERT INTO users(id, phone, full_name, role, active, verified_at, account_status)
                VALUES (?, ?, ?, ?, true, now(), 'ACTIVE')
                """, id, phone, "Performance " + role, role);
    }
}
