package kz.afm.kendala.stage3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kz.afm.kendala.auth.enums.OtpPurpose;
import kz.afm.kendala.auth.repository.OtpCodeRepository;
import kz.afm.kendala.auth.repository.RefreshTokenRepository;
import kz.afm.kendala.auth.service.FakeSmsSender;
import kz.afm.kendala.auth.service.JwtService;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.repository.UserRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AuthIntegrationTest {

    static final String PHONE_RAW = "87001234567";
    static final String PHONE_NORM = "+77001234567";
    static final String FULL_NAME = "Test Applicant";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FakeSmsSender fakeSmsSender;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired OtpCodeRepository otpCodeRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanup() {
        refreshTokenRepository.deleteAll();
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
    }

    // #1 Регистрация не принимает роль в теле запроса
    @Test
    void register_doesNotAcceptRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","fullName":"%s","role":"ADMIN"}
                                """.formatted(PHONE_RAW, FULL_NAME)))
                .andExpect(status().isAccepted());

        User user = userRepository.findByPhone(PHONE_NORM).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.APPLICANT);
    }

    // #2 Регистрация создаёт APPLICANT
    @Test
    void register_createsApplicant() throws Exception {
        register();
        User user = userRepository.findByPhone(PHONE_NORM).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.APPLICANT);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getVerifiedAt()).isNull();
    }

    // #3 Телефон нормализуется
    @Test
    void register_normalizesPhone() throws Exception {
        // input: 87001234567 → stored: +77001234567
        register();
        assertThat(userRepository.findByPhone(PHONE_NORM)).isPresent();
        assertThat(userRepository.findByPhone(PHONE_RAW)).isEmpty();
    }

    // #4 Повторная регистрация не создаёт дубль
    @Test
    void register_duplicatePhoneReturnsNeutralAccepted() throws Exception {
        User existing = new User();
        existing.setPhone(PHONE_NORM);
        existing.setFullName(FULL_NAME);
        existing.setRole(UserRole.APPLICANT);
        existing.setAccountStatus(UserAccountStatus.ACTIVE);
        userRepository.saveAndFlush(existing);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isAccepted());

        assertThat(userRepository.count()).isEqualTo(1L);
    }

    // #5 OTP запрошен
    @Test
    void register_sendsOtp() throws Exception {
        register();
        assertThat(fakeSmsSender.getLastCode(PHONE_NORM)).isNotNull();
    }

    // #6 OTP хранится не открытым текстом
    @Test
    void otp_storedAsHash() throws Exception {
        register();
        String code = fakeSmsSender.getLastCode(PHONE_NORM);
        String hash = otpCodeRepository
                .findTopByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE_NORM, OtpPurpose.REGISTRATION)
                .orElseThrow().getCodeHash();
        assertThat(hash).isNotEqualTo(code);
        assertThat(hash.length()).isGreaterThan(10);
    }

    // #7 Неверный OTP возвращает 401
    @Test
    void verify_wrongCodeReturns401() throws Exception {
        register();

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody("000000", OtpPurpose.REGISTRATION)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // #8 Просроченный OTP
    @Test
    void verify_expiredOtpReturns401() throws Exception {
        register();
        // Expire the OTP manually
        var otp = otpCodeRepository
                .findTopByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE_NORM, OtpPurpose.REGISTRATION)
                .orElseThrow();
        otp.setExpiresAt(java.time.Instant.now().minusSeconds(1));
        otpCodeRepository.save(otp);

        String code = fakeSmsSender.getLastCode(PHONE_NORM);
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andExpect(status().isUnauthorized());
    }

    // #9 Повторное использование OTP
    @Test
    void verify_reusedOtpReturns401() throws Exception {
        register();
        String code = fakeSmsSender.getLastCode(PHONE_NORM);

        // First use: success
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andExpect(status().isOk());

        // Second use: fail
        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify_concurrentOtpUseAllowsExactlyOneSuccess() throws Exception {
        register();
        String code = fakeSmsSender.getLastCode(PHONE_NORM);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentVerify(code, ready, start));
            var second = executor.submit(() -> concurrentVerify(code, ready, start));
            ready.await();
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(200, 401);
        }
    }

    // #10 Лимит попыток
    @Test
    void verify_maxAttemptsExceededReturns429() throws Exception {
        register();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(verifyBody("000000", OtpPurpose.REGISTRATION)));
        }

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody("000000", OtpPurpose.REGISTRATION)))
                .andExpect(status().isTooManyRequests());
    }

    // #11 Resend cooldown
    @Test
    void register_resendCooldownReturns429() throws Exception {
        registerAndVerify(); // create user + consume REGISTRATION OTP
        otpCodeRepository.deleteAll();

        // First login OTP
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
                .andExpect(status().isAccepted());

        // Immediate resend → cooldown
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody()))
                .andExpect(status().isTooManyRequests());
    }

    // #12 Успешный verify возвращает токены и пользователя
    @Test
    void verify_successReturnsTokensAndUser() throws Exception {
        register();
        String code = fakeSmsSender.getLastCode(PHONE_NORM);

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.phone").value(PHONE_NORM))
                .andExpect(jsonPath("$.user.role").value("APPLICANT"));

        User verified = userRepository.findByPhone(PHONE_NORM).orElseThrow();
        assertThat(verified.isActive()).isTrue();
        assertThat(verified.getVerifiedAt()).isNotNull();
    }

    // #13 Access token разрешает защищённый endpoint
    @Test
    void accessToken_allowsProtectedEndpoint() throws Exception {
        String accessToken = registerAndVerify().get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE_NORM));
    }

    // #14 Запрос без JWT получает 401
    @Test
    void request_withoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // #15 Повреждённый JWT получает 401
    @Test
    void request_withBrokenJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/applications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // #16 Refresh token rotation
    @Test
    void refresh_rotatesToken() throws Exception {
        String oldRefresh = registerAndVerify().get("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(oldRefresh)))
                .andExpect(status().isOk())
                .andReturn();

        String newRefresh = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);
    }

    // #17 Отозванный refresh token не работает
    @Test
    void refresh_revokedTokenReturns401() throws Exception {
        String refreshToken = registerAndVerify().get("refreshToken").asText();

        // Use it once (rotates)
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk());

        // Use old token again
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    // #18 Logout
    @Test
    void logout_revokesRefreshToken() throws Exception {
        JsonNode tokens = registerAndVerify();
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isNoContent());

        // Refresh after logout fails
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    // #19 /api/auth/me
    @Test
    void me_returnsCurrentUser() throws Exception {
        String accessToken = registerAndVerify().get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(PHONE_NORM))
                .andExpect(jsonPath("$.fullName").value(FULL_NAME))
                .andExpect(jsonPath("$.role").value("APPLICANT"));
    }

    // #20 X-User-Id больше не даёт доступ без JWT
    @Test
    void xUserId_doesNotGrantAccess() throws Exception {
        mockMvc.perform(get("/api/applications")
                        .header("X-User-Id", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isUnauthorized());
    }

    // --- Helpers ---

    private void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isAccepted());
    }

    private JsonNode registerAndVerify() throws Exception {
        register();
        String code = fakeSmsSender.getLastCode(PHONE_NORM);
        MvcResult result = mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String registerBody() {
        return """
                {"phone":"%s","fullName":"%s"}
                """.formatted(PHONE_RAW, FULL_NAME);
    }

    private String loginBody() {
        return """
                {"phone":"%s"}
                """.formatted(PHONE_NORM);
    }

    private String verifyBody(String code, OtpPurpose purpose) {
        return """
                {"phone":"%s","code":"%s","purpose":"%s"}
                """.formatted(PHONE_NORM, code, purpose.name());
    }

    private int concurrentVerify(
            String code,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(code, OtpPurpose.REGISTRATION)))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
