package kz.afm.kendala.stageC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.application.service.ApplicationNumberGenerator;
import kz.afm.kendala.commission.CommissionService;
import kz.afm.kendala.ai.AiOperationType;
import kz.afm.kendala.ai.AiProcessingJob;
import kz.afm.kendala.ai.AiProcessingJobRepository;
import kz.afm.kendala.ai.AiTaskStatus;
import kz.afm.kendala.ai.ApplicationScore;
import kz.afm.kendala.ai.ApplicationScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
class ConcurrentDecisionTest {

    static final UUID APPLICANT_ID = UUID.fromString("dd000000-0000-0000-0000-000000000001");
    static final UUID CHAIRMAN_ID  = UUID.fromString("dd000000-0000-0000-0000-000000000002");

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
    @Autowired UserRepository userRepository;
    @Autowired ApplicationRepository applicationRepository;
    @Autowired ApplicationStatusHistoryRepository historyRepository;
    @Autowired DecisionRepository decisionRepository;
    @Autowired ApplicationNumberGenerator numberGenerator;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AiProcessingJobRepository aiJobRepository;
    @Autowired ApplicationScoreRepository scoreRepository;
    @SpyBean CommissionService commissionService;

    @BeforeEach
    void setUp() {
        Mockito.reset(commissionService);
        decisionRepository.deleteAll();
        scoreRepository.deleteAll();
        aiJobRepository.deleteAll();
        historyRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();
        createUser(APPLICANT_ID, "+77070000001", UserRole.APPLICANT);
        createUser(CHAIRMAN_ID,  "+77070000002", UserRole.CHAIRMAN);
    }

    // 1. version field exists and is initialized to 0
    @Test
    void application_hasVersionField() {
        UUID appId = createAppDirect();
        Application app = applicationRepository.findById(appId).orElseThrow();
        assertThat(app.getVersion()).isNotNull();
        assertThat(app.getVersion()).isGreaterThanOrEqualTo(0L);
    }

    // 2. version increments on each modification
    @Test
    void version_incrementsOnModification() {
        UUID appId = createAppDirect();
        Application app = applicationRepository.findById(appId).orElseThrow();
        long v0 = app.getVersion();

        app.setRegion("Modified");
        applicationRepository.saveAndFlush(app);

        long v1 = applicationRepository.findById(appId).orElseThrow().getVersion();
        assertThat(v1).isGreaterThan(v0);
    }

    // 3. stale update (version mismatch) throws ObjectOptimisticLockingFailureException
    @Test
    void staleUpdate_throwsObjectOptimisticLockingFailureException() {
        UUID appId = createAppDirect();
        Application stale = applicationRepository.findById(appId).orElseThrow();

        // Simulate concurrent modification: bump DB version directly via JDBC
        jdbcTemplate.update("UPDATE applications SET version = version + 1 WHERE id = ?", appId);

        stale.setRegion("Stale modification");
        assertThatThrownBy(() -> applicationRepository.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // 4. concurrent decision returns HTTP 409
    @Test
    void concurrentDecision_returns409() throws Exception {
        UUID appId = createSubmittedApp();

        doThrow(new ObjectOptimisticLockingFailureException(Application.class, appId))
                .when(commissionService).approve(eq(appId), any(), any(), nullable(BigDecimal.class));

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    // 5. code equals CONCURRENT_MODIFICATION
    @Test
    void concurrentDecision_codeIsConcurrentModification() throws Exception {
        UUID appId = createSubmittedApp();

        doThrow(new ObjectOptimisticLockingFailureException(Application.class, appId))
                .when(commissionService).approve(eq(appId), any(), any(), nullable(BigDecimal.class));

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    // 6. conflict creates no extra Decision or StatusHistory
    @Test
    void conflict_createsNoDecisionOrHistory() throws Exception {
        UUID appId = createSubmittedApp();
        long decisionsBefore = decisionRepository.count();
        long historyBefore   = historyRepository.count();

        doThrow(new ObjectOptimisticLockingFailureException(Application.class, appId))
                .when(commissionService).approve(eq(appId), any(), any(), nullable(BigDecimal.class));

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        assertThat(decisionRepository.count()).isEqualTo(decisionsBefore);
        assertThat(historyRepository.count()).isEqualTo(historyBefore);
    }

    // 7. normal approve continues to work after optimistic locking is added
    @Test
    void normalApprove_continues_toWork() throws Exception {
        UUID appId = createReviewableApp();

        mockMvc.perform(post("/api/commission/applications/{id}/approve", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"All good\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // 8. normal reject continues to work
    @Test
    void normalReject_continues_toWork() throws Exception {
        UUID appId = createReviewableApp();

        mockMvc.perform(post("/api/commission/applications/{id}/reject", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Insufficient docs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // 9. normal request-documents continues to work
    @Test
    void normalRequestDocuments_continues_toWork() throws Exception {
        UUID appId = createSubmittedApp();

        mockMvc.perform(post("/api/commission/applications/{id}/request-documents", appId)
                        .with(user(CHAIRMAN_ID.toString()).roles("CHAIRMAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Need originals\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ADDITIONAL_DOCUMENTS_REQUESTED"));
    }

    // --- helpers ---

    private void createUser(UUID id, String phone, UserRole role) {
        User u = new User();
        u.setId(id); u.setPhone(phone); u.setFullName("User " + role); u.setRole(role);
        userRepository.save(u);
    }

    private UUID createAppDirect() {
        User applicant = userRepository.findById(APPLICANT_ID).orElseThrow();
        Application app = new Application();
        app.setApplicationNumber(numberGenerator.generate());
        app.setApplicant(applicant);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setRequestedAmount(BigDecimal.valueOf(100));
        return applicationRepository.save(app).getId();
    }

    private UUID createSubmittedApp() {
        UUID id = createAppDirect();
        Application app = applicationRepository.findById(id).orElseThrow();
        app.setSubmissionRevision(1);
        app.setStatus(ApplicationStatus.SUBMITTED);
        applicationRepository.save(app);
        return id;
    }

    private UUID createReviewableApp() {
        UUID id = createAppDirect();
        Application app = applicationRepository.findById(id).orElseThrow();
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
        score.setApplicationId(id);
        score.setSubmissionRevision(1);
        score.setJob(job);
        score.setScore(new BigDecimal("89.00"));
        score.setRiskCategory("LOW");
        score.setRecommendedAmount(new BigDecimal("1000000.00"));
        score.setTopFactors("[]");
        score.setModelName("concurrency-test");
        score.setModelVersion("test-v1");
        score.setModelMetadata("{}");
        score.setCheckedAt(Instant.now());
        scoreRepository.saveAndFlush(score);
        return id;
    }
}
