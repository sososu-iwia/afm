package kz.afm.kendala.useradmin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.auth.repository.RefreshTokenRepository;
import kz.afm.kendala.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminUserService service = new AdminUserService(
            userRepository,
            refreshTokenRepository,
            auditService
    );

    @Test
    void adminCannotChangeOwnRole() {
        User admin = user(UUID.randomUUID(), UserRole.ADMIN);
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.changeRole(admin, admin.getId(), UserRole.APPLICANT))
                .isInstanceOf(BusinessRuleException.class);
    }

    private User user(UUID id, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setPhone("+77000000000");
        user.setFullName("Admin User");
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}
