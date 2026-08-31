package kz.afm.kendala.auth.service;

import java.time.Instant;
import kz.afm.kendala.auth.enums.OtpPurpose;
import kz.afm.kendala.auth.repository.OtpCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Increments the OTP attempt counter in a dedicated REQUIRES_NEW transaction so the
 * increment is committed even when the caller's transaction rolls back on invalid code.
 */
@Service
class OtpAttemptService {

    private final OtpCodeRepository repo;
    private final PasswordEncoder passwordEncoder;

    OtpAttemptService(OtpCodeRepository repo, PasswordEncoder passwordEncoder) {
        this.repo = repo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    VerificationResult verify(String phone, String code, OtpPurpose purpose, int maxAttempts) {
        var otp = repo.findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, purpose)
                .orElse(null);
        if (otp == null) {
            return VerificationResult.NOT_FOUND;
        }
        if (otp.getInvalidatedAt() != null && otp.getAttempts() >= otp.getMaximumAttempts()) {
            return VerificationResult.ATTEMPTS_EXHAUSTED;
        }
        if (otp.getInvalidatedAt() != null) {
            return VerificationResult.NOT_FOUND;
        }
        if (Instant.now().isAfter(otp.getExpiresAt())) {
            otp.setInvalidatedAt(Instant.now());
            return VerificationResult.EXPIRED;
        }
        int allowedAttempts = Math.min(maxAttempts, otp.getMaximumAttempts());
        if (otp.getAttempts() >= allowedAttempts) {
            otp.setInvalidatedAt(Instant.now());
            return VerificationResult.ATTEMPTS_EXHAUSTED;
        }
        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            if (otp.getAttempts() >= allowedAttempts) {
                otp.setInvalidatedAt(Instant.now());
            }
            return VerificationResult.INVALID;
        }
        otp.setConsumedAt(Instant.now());
        return VerificationResult.SUCCESS;
    }

    enum VerificationResult {
        SUCCESS,
        NOT_FOUND,
        EXPIRED,
        ATTEMPTS_EXHAUSTED,
        INVALID
    }
}
