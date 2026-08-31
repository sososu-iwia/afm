package kz.afm.kendala.stage1;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.common.CurrentUserProvider;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class DevAuthDisabledTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserRequiresAuthenticatedSecurityPrincipal() {
        CurrentUserProvider provider = new CurrentUserProvider(mock(UserRepository.class));

        assertThatThrownBy(provider::getCurrentUser)
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void legacyDevProviderIsNotOnRuntimeClasspath() {
        assertThatThrownBy(() -> Class.forName("kz.afm.kendala.dev.DevCurrentUserProvider"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
