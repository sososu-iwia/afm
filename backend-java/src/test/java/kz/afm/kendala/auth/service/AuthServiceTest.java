package kz.afm.kendala.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.auth.entity.RefreshToken;
import kz.afm.kendala.auth.repository.RefreshTokenRepository;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final OtpService otpService = mock(OtpService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final PhoneNormalizer phoneNormalizer = new PhoneNormalizer();
    private final AuthRateLimitService rateLimitService = mock(AuthRateLimitService.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AuthService service = new AuthService(
            userRepository,
            refreshTokenRepository,
            otpService,
            jwtService,
            phoneNormalizer,
            rateLimitService,
            auditService,
            3600
    );

    @Test
    void refreshReuseRevokesTokenFamily() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setPhone("+77000000000");
        user.setFullName("User");
        user.setRole(UserRole.APPLICANT);
        user.setActive(true);

        UUID familyId = UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash("hash");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevokedAt(Instant.now());
        token.setRevokeReason("ROTATED");
        token.setReplacedByTokenId(UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh("raw-token", "JUnit", "127.0.0.1"))
                .isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepository).revokeFamily(eq(familyId), eq("REUSE_DETECTED"));
        verify(auditService).record(
                anyString(), anyString(), eq("REFRESH_REUSE_DETECTED"), eq("AUTH"),
                anyString(), any(), any(), anyString(), any()
        );
    }
}
