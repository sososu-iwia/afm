package kz.afm.kendala.stageA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.auth.repository.OtpCodeRepository;
import kz.afm.kendala.auth.service.FakeSmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LoginEnumerationTest {

    static final String KNOWN_PHONE   = "+77001110001";
    static final String UNKNOWN_PHONE = "+77001119999";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired OtpCodeRepository otpCodeRepository;
    @Autowired FakeSmsSender fakeSmsSender;

    @BeforeEach
    void setUp() {
        otpCodeRepository.deleteAll();
        userRepository.deleteAll();
        User u = new User();
        u.setPhone(KNOWN_PHONE);
        u.setFullName("Known User");
        u.setRole(UserRole.APPLICANT);
        userRepository.save(u);
    }

    // известный телефон → 202 с нейтральным сообщением
    @Test
    void login_knownPhone_returns202WithNeutralMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + KNOWN_PHONE + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Если номер зарегистрирован, код подтверждения будет отправлен"));
    }

    // неизвестный телефон → тот же 202 с тем же сообщением
    @Test
    void login_unknownPhone_returns202WithSameMessage() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + UNKNOWN_PHONE + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Если номер зарегистрирован, код подтверждения будет отправлен"));
    }

    // неизвестному телефону SMS не отправляется
    @Test
    void login_unknownPhone_noSmsQueued() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + UNKNOWN_PHONE + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(fakeSmsSender.getLastMessage(UNKNOWN_PHONE)).isNull();
    }

    // login не создаёт пользователя
    @Test
    void login_unknownPhone_userNotCreated() throws Exception {
        long countBefore = userRepository.count();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + UNKNOWN_PHONE + "\"}"))
                .andExpect(status().isAccepted());

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    // телефон нормализуется до поиска: "87001110001" → "+77001110001"
    @Test
    void login_phoneNormalization_smsQueuedForKnownUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"87001110001\"}"))
                .andExpect(status().isAccepted());

        assertThat(fakeSmsSender.getLastMessage(KNOWN_PHONE)).isNotNull();
    }
}
