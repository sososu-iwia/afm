package kz.afm.kendala.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.auth.dto.AuthResponse;
import kz.afm.kendala.auth.dto.UserResponse;
import kz.afm.kendala.auth.entity.RefreshToken;
import kz.afm.kendala.auth.enums.OtpPurpose;
import kz.afm.kendala.auth.repository.RefreshTokenRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final String AUTH_FAILURE_MESSAGE = "Неверный код или назначение кода";
    private static final String AUTH_FAILURE_KEY = "error.otp.invalid";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final PhoneNormalizer phoneNormalizer;
    private final AuthRateLimitService rateLimitService;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshTtlSeconds;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            OtpService otpService,
            JwtService jwtService,
            PhoneNormalizer phoneNormalizer,
            AuthRateLimitService rateLimitService,
            AuditService auditService,
            @Value("${app.jwt.refresh-ttl-seconds:2592000}") long refreshTtlSeconds
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.phoneNormalizer = phoneNormalizer;
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @Transactional
    public void register(String rawPhone, String fullName, String email, String remoteAddress) {
        String phone = phoneNormalizer.normalize(rawPhone);
        rateLimitService.checkAndRecord(phone, remoteAddress);
        String normalizedEmail = email == null || email.isBlank() ? null : email.strip().toLowerCase();
        if (normalizedEmail != null && userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !existing.getPhone().equals(phone))
                .isPresent()) {
            normalizedEmail = null;
        }

        var existing = userRepository.findByPhone(phone);
        auditAuth("REGISTER_ATTEMPT", null, Map.of("phoneMasked", maskPhone(phone)));
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getAccountStatus() == UserAccountStatus.PENDING_VERIFICATION) {
                user.setFullName(fullName);
                if (normalizedEmail != null) {
                    user.setEmail(normalizedEmail);
                }
                userRepository.save(user);
                otpService.sendOtp(phone, OtpPurpose.REGISTRATION);
            }
            return;
        }

        User user = new User();
        user.setPhone(phone);
        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setRole(UserRole.APPLICANT);
        user.setAccountStatus(UserAccountStatus.PENDING_VERIFICATION);
        userRepository.save(user);
        otpService.sendOtp(phone, OtpPurpose.REGISTRATION);
        auditAuth("REGISTER_SUCCESS", user, Map.of("userId", user.getId()));
    }

    @Transactional
    public void login(String rawPhone, String remoteAddress) {
        String phone = phoneNormalizer.normalize(rawPhone);
        rateLimitService.checkAndRecord(phone, remoteAddress);
        var user = userRepository.findByPhone(phone).orElse(null);
        if (user != null && canAuthenticate(user)) {
            otpService.sendOtp(phone, OtpPurpose.LOGIN);
        }
        auditAuth("OTP_REQUESTED", user, Map.of(
                "purpose", OtpPurpose.LOGIN.name(),
                "phoneMasked", maskPhone(phone)
        ));
    }

    @Transactional
    public AuthResponse verify(String rawPhone, String code, OtpPurpose purpose, String userAgent, String remoteAddress) {
        String phone = phoneNormalizer.normalize(rawPhone);
        User user = userRepository.findByPhone(phone).orElse(null);
        if (user == null || purpose == OtpPurpose.PASSWORD_RESET || purpose == OtpPurpose.PHONE_CHANGE) {
            auditAuth("OTP_FAILED", user, Map.of("purpose", purpose.name()));
            throw authFailure();
        }

        if (purpose == OtpPurpose.LOGIN && !canAuthenticate(user)) {
            auditAuth("OTP_FAILED", user, Map.of("purpose", purpose.name(), "reason", user.getAccountStatus().name()));
            throw authFailure();
        }
        if (purpose == OtpPurpose.REGISTRATION
                && user.getAccountStatus() != UserAccountStatus.PENDING_VERIFICATION) {
            auditAuth("OTP_FAILED", user, Map.of("purpose", purpose.name(), "reason", user.getAccountStatus().name()));
            throw authFailure();
        }
        try {
            otpService.verifyOtp(phone, code, purpose);
            auditAuth("OTP_VERIFIED", user, Map.of("purpose", purpose.name()));
        } catch (RuntimeException exception) {
            auditAuth("OTP_FAILED", user, Map.of("purpose", purpose.name(), "reason", exception.getClass().getSimpleName()));
            throw exception;
        }

        if (purpose == OtpPurpose.REGISTRATION) {
            user.setAccountStatus(UserAccountStatus.ACTIVE);
            user.setVerifiedAt(Instant.now());
            userRepository.save(user);
        }

        AuthResponse response = issueTokens(user, UUID.randomUUID(), userAgent, remoteAddress);
        auditAuth(purpose == OtpPurpose.LOGIN ? "LOGIN_SUCCESS" : "REGISTER_SUCCESS", user, Map.of("session", "created"));
        return response;
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String remoteAddress) {
        String hash = sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Недействительный refresh token"));

        if (stored.getRevokedAt() != null) {
            if ("ROTATED".equals(stored.getRevokeReason()) || stored.getReplacedByTokenId() != null) {
                refreshTokenRepository.revokeFamily(stored.getFamilyId(), "REUSE_DETECTED");
                auditAuth("REFRESH_REUSE_DETECTED", stored.getUser(), Map.of("familyId", stored.getFamilyId()));
            }
            throw new UnauthorizedException("Недействительный refresh token");
        }
        if (Instant.now().isAfter(stored.getExpiresAt())) {
            stored.setRevokedAt(Instant.now());
            stored.setRevokeReason("EXPIRED");
            throw new UnauthorizedException("Недействительный refresh token");
        }
        if (!canAuthenticate(stored.getUser())) {
            refreshTokenRepository.revokeFamily(stored.getFamilyId(), "ACCOUNT_NOT_ACTIVE");
            throw new UnauthorizedException("Недействительный refresh token");
        }

        stored.setLastUsedAt(Instant.now());
        AuthResponse response = issueTokens(stored.getUser(), stored.getFamilyId(), userAgent, remoteAddress);
        String newHash = sha256Hex(response.refreshToken());
        RefreshToken replacement = refreshTokenRepository.findByTokenHash(newHash)
                .orElseThrow(() -> new IllegalStateException("Refresh token rotation failed"));

        stored.setRevokedAt(Instant.now());
        stored.setRevokeReason("ROTATED");
        stored.setReplacedByTokenId(replacement.getId());
        auditAuth("TOKEN_REFRESHED", stored.getUser(), Map.of("sessionId", replacement.getId()));
        return response;
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                token.setRevokeReason("LOGOUT");
                refreshTokenRepository.save(token);
            }
            auditAuth("LOGOUT", token.getUser(), Map.of("sessionId", token.getId()));
        });
    }

    @Transactional
    public void logoutAll(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), "LOGOUT_ALL");
        auditAuth("LOGOUT_ALL", user, Map.of("userId", user.getId()));
    }

    @Transactional
    public User updateProfile(User user, String fullName, String email) {
        User managed = userRepository.findById(user.getId())
                .orElseThrow(() -> new UnauthorizedException("Недействительная учётная запись"));
        String previousName = managed.getFullName();
        String previousEmail = managed.getEmail();

        managed.setFullName(fullName.trim());
        // Пустая строка из формы означает "адрес не указан", а не пустой email.
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim();
        managed.setEmail(normalizedEmail);
        User saved = userRepository.save(managed);

        auditAuth("PROFILE_UPDATED", saved, Map.of(
                "userId", saved.getId(),
                "fullNameChanged", !java.util.Objects.equals(previousName, saved.getFullName()),
                "emailChanged", !java.util.Objects.equals(previousEmail, saved.getEmail())));
        return saved;
    }

    private AuthResponse issueTokens(User user, UUID familyId, String userAgent, String remoteAddress) {
        if (!canAuthenticate(user)) {
            throw new UnauthorizedException("Недействительная учётная запись");
        }
        String rawRefreshToken = generateRawToken();
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(sha256Hex(rawRefreshToken));
        rt.setFamilyId(familyId);
        rt.setIssuedAt(Instant.now());
        rt.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds));
        rt.setUserAgent(safe(userAgent, 255));
        rt.setIpAddress(safe(remoteAddress, 64));
        refreshTokenRepository.save(rt);

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        return new AuthResponse(accessToken, rawRefreshToken, UserResponse.from(user));
    }

    private boolean canAuthenticate(User user) {
        return user.getAccountStatus() == UserAccountStatus.ACTIVE && user.isVerified();
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private UnauthorizedException authFailure() {
        return new UnauthorizedException(AUTH_FAILURE_MESSAGE, AUTH_FAILURE_KEY);
    }

    private void auditAuth(String action, User user, Map<String, ?> metadata) {
        auditService.record(
                user == null ? "anonymous" : user.getId().toString(),
                user == null ? "ANONYMOUS" : user.getRole().name(),
                action,
                "AUTH",
                user == null ? "-" : user.getId().toString(),
                null,
                new LinkedHashMap<>(metadata),
                "system",
                MDC.get("correlationId")
        );
    }

    private String safe(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.substring(0, Math.min(stripped.length(), max));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }
}
