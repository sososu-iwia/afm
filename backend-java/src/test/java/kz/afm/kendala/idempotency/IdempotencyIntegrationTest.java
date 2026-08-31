package kz.afm.kendala.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import kz.afm.kendala.application.entity.Application;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.repository.ApplicationStatusHistoryRepository;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.application.service.ApplicationNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
@TestPropertySource(properties = {
        "app.documents.required-types=",
        "app.idempotency.ttl-seconds=3600"
})
class IdempotencyIntegrationTest {

    private static final UUID APPLICANT_ONE =
            UUID.fromString("ed000000-0000-0000-0000-000000000001");
    private static final UUID APPLICANT_TWO =
            UUID.fromString("ed000000-0000-0000-0000-000000000002");
    private static final UUID CHAIRMAN =
            UUID.fromString("ed000000-0000-0000-0000-000000000003");

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
    UserRepository userRepository;
    @Autowired
    ApplicationRepository applicationRepository;
    @Autowired
    ApplicationStatusHistoryRepository historyRepository;
    @Autowired
    ApplicationNumberGenerator numberGenerator;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        createUser(APPLICANT_ONE, "+77070001001", UserRole.APPLICANT);
        createUser(APPLICANT_TWO, "+77070001002", UserRole.APPLICANT);
        createUser(CHAIRMAN, "+77070001003", UserRole.CHAIRMAN);
    }

    @Test
    void concurrentSameKeyReturnsSameResponseAndCreatesOneTransition() throws Exception {
        UUID applicationId = createApplication(APPLICANT_ONE, ApplicationStatus.DRAFT);
        String key = "withdraw-concurrent-001";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> invokeWithdraw(applicationId, APPLICANT_ONE, key, ready, start));
            var second = executor.submit(() -> invokeWithdraw(applicationId, APPLICANT_ONE, key, ready, start));
            ready.await();
            start.countDown();

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getContentAsString())
                    .isEqualTo(firstResult.getResponse().getContentAsString());
        }

        assertThat(historyRepository.findByApplicationIdOrderByCreatedAtAsc(applicationId))
                .hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?",
                Integer.class,
                key
        )).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadReturnsConflict() throws Exception {
        UUID applicationId = createApplication(APPLICANT_ONE, ApplicationStatus.SUBMITTED);
        Application application = applicationRepository.findById(applicationId).orElseThrow();
        application.setSubmissionRevision(1);
        applicationRepository.saveAndFlush(application);
        String key = "approve-payload-001";

        MvcResult first = mockMvc.perform(post(
                                "/api/commission/applications/{id}/request-documents",
                                applicationId
                        )
                        .with(user(CHAIRMAN.toString()).roles("CHAIRMAN"))
                        .header(IdempotencyService.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"first reason\",\"documentTypes\":[\"BANK_STATEMENT\"]}"))
                .andReturn();
        MvcResult second = mockMvc.perform(post(
                                "/api/commission/applications/{id}/request-documents",
                                applicationId
                        )
                        .with(user(CHAIRMAN.toString()).roles("CHAIRMAN"))
                        .header(IdempotencyService.HEADER, key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"different reason\",\"documentTypes\":[\"BANK_STATEMENT\"]}"))
                .andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void sameKeyIsScopedByUser() throws Exception {
        UUID firstApp = createApplication(APPLICANT_ONE, ApplicationStatus.DRAFT);
        UUID secondApp = createApplication(APPLICANT_TWO, ApplicationStatus.DRAFT);
        String key = "user-scope-001";

        assertThat(invokeWithdraw(firstApp, APPLICANT_ONE, key).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(invokeWithdraw(secondApp, APPLICANT_TWO, key).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM idempotency_records WHERE idempotency_key = ?",
                Integer.class,
                key
        )).isEqualTo(2);
    }

    @Test
    void requestWithoutKeyKeepsExistingStateProtection() throws Exception {
        UUID applicationId = createApplication(APPLICANT_ONE, ApplicationStatus.DRAFT);

        assertThat(invokeWithdraw(applicationId, APPLICANT_ONE, null).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(invokeWithdraw(applicationId, APPLICANT_ONE, null).getResponse().getStatus())
                .isEqualTo(409);
    }

    @Test
    void concurrentProtocolGenerationCreatesSingleStoredProtocol() throws Exception {
        UUID applicationId = createApplication(APPLICANT_ONE, ApplicationStatus.APPROVED);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> invokeProtocol(applicationId, ready, start));
            var second = executor.submit(() -> invokeProtocol(applicationId, ready, start));
            ready.await();
            start.countDown();

            MvcResult firstResult = first.get();
            MvcResult secondResult = second.get();
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getContentAsByteArray())
                    .isEqualTo(firstResult.getResponse().getContentAsByteArray());
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM generated_protocols WHERE application_id = ?",
                Integer.class,
                applicationId
        )).isEqualTo(1);
    }

    private MvcResult invokeWithdraw(
            UUID applicationId,
            UUID actorId,
            String key,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return invokeWithdraw(applicationId, actorId, key);
    }

    private MvcResult invokeWithdraw(UUID applicationId, UUID actorId, String key)
            throws Exception {
        var request = post("/api/applications/{id}/withdraw", applicationId)
                .with(user(actorId.toString()).roles("APPLICANT"));
        if (key != null) {
            request.header(IdempotencyService.HEADER, key);
        }
        return mockMvc.perform(request).andReturn();
    }

    private MvcResult invokeProtocol(
            UUID applicationId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post(
                                "/api/commission/applications/{id}/generate-protocol",
                                applicationId
                        )
                        .with(user(CHAIRMAN.toString()).roles("CHAIRMAN")))
                .andReturn();
    }

    private void createUser(UUID id, String phone, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setPhone(phone);
        user.setFullName("Idempotency " + role);
        user.setRole(role);
        user.setActive(true);
        userRepository.save(user);
    }

    private UUID createApplication(UUID applicantId, ApplicationStatus status) {
        Application application = new Application();
        application.setApplicationNumber(numberGenerator.generate());
        application.setApplicant(userRepository.findById(applicantId).orElseThrow());
        application.setStatus(status);
        application.setIinOrBin("123456789012");
        application.setRegion("Akmola");
        application.setProductionType("GRAIN");
        application.setLandArea(java.math.BigDecimal.TEN);
        application.setRequestedAmount(java.math.BigDecimal.valueOf(100));
        return applicationRepository.save(application).getId();
    }
}
