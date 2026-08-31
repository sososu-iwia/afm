package kz.afm.kendala.useradmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.auth.service.PhoneNormalizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AdminBootstrapRunnerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminBootstrapRunner runner =
            new AdminBootstrapRunner(userRepository, new PhoneNormalizer());

    private void configure(String phone, String name) {
        ReflectionTestUtils.setField(runner, "adminPhone", phone);
        ReflectionTestUtils.setField(runner, "adminName", name);
    }

    @Test
    void createsAdminWhenPhoneIsNotRegisteredYet() {
        configure("+7 700 000 00 00", "Томирис Жусупова");
        when(userRepository.findByPhone("+77000000000")).thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.getValue().getAccountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
        assertThat(saved.getValue().getPhone()).isEqualTo("+77000000000");
        assertThat(saved.getValue().getFullName()).isEqualTo("Томирис Жусупова");
    }

    @Test
    void promotesExistingApplicantToAdmin() {
        configure("+77000000000", "Администратор системы");
        User existing = new User();
        existing.setPhone("+77000000000");
        existing.setRole(UserRole.APPLICANT);
        existing.setAccountStatus(UserAccountStatus.PENDING_VERIFICATION);
        when(userRepository.findByPhone("+77000000000")).thenReturn(Optional.of(existing));

        runner.run(null);

        verify(userRepository).save(existing);
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(existing.getAccountStatus()).isEqualTo(UserAccountStatus.ACTIVE);
    }

    @Test
    void repeatedStartDoesNotRewriteExistingAdmin() {
        configure("+77000000000", "Администратор системы");
        User existing = new User();
        existing.setPhone("+77000000000");
        existing.setRole(UserRole.ADMIN);
        existing.setAccountStatus(UserAccountStatus.ACTIVE);
        when(userRepository.findByPhone("+77000000000")).thenReturn(Optional.of(existing));

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenVariableIsEmpty() {
        configure("", "Администратор системы");

        runner.run(null);

        verify(userRepository, never()).findByPhone(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void invalidPhoneDoesNotBreakStartup() {
        configure("не-номер", "Администратор системы");

        runner.run(null);

        verify(userRepository, never()).save(any());
    }
}
