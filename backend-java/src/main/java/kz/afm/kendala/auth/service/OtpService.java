package kz.afm.kendala.auth.service;

import java.security.SecureRandom;
import java.time.Instant;
import kz.afm.kendala.auth.entity.OtpCode;
import kz.afm.kendala.auth.enums.OtpPurpose;
import kz.afm.kendala.auth.repository.OtpCodeRepository;
import kz.afm.kendala.common.exception.TooManyRequestsException;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final SmsService smsService;
    private final PasswordEncoder passwordEncoder;
    private final OtpAttemptService otpAttemptService;
    private final SecureRandom secureRandom = new SecureRandom();

    private final long ttlSeconds;
    private final long resendCooldownSeconds;
    private final int maxAttempts;

    public OtpService(
            OtpCodeRepository otpCodeRepository,
            SmsService smsService,
            PasswordEncoder passwordEncoder,
            OtpAttemptService otpAttemptService,
            @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.otp.max-attempts:5}") int maxAttempts
    ) {
        this.otpCodeRepository = otpCodeRepository;
        this.smsService = smsService;
        this.passwordEncoder = passwordEncoder;
        this.otpAttemptService = otpAttemptService;
        this.ttlSeconds = ttlSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxAttempts = maxAttempts;
    }

    @Transactional
    public void sendOtp(String phone, OtpPurpose purpose) {
        otpCodeRepository.findTopByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, purpose)
                .ifPresent(existing -> {
                    Instant cooldownEnd = existing.getCooldownUntil() == null
                            ? existing.getLastSentAt().plusSeconds(resendCooldownSeconds)
                            : existing.getCooldownUntil();
                    if (Instant.now().isBefore(cooldownEnd)) {
                        throw new TooManyRequestsException("Повторная отправка кода возможна через " + resendCooldownSeconds + " секунд");
                    }
                });

        otpCodeRepository.invalidateActiveOtps(phone, purpose);

        String code = generateCode();
        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setPurpose(purpose);
        otp.setExpiresAt(Instant.now().plusSeconds(ttlSeconds));
        otp.setMaximumAttempts(maxAttempts);
        otp.setCooldownUntil(Instant.now().plusSeconds(resendCooldownSeconds));
        otp.setRequestCorrelationId(MDC.get("correlationId"));
        otp.setProviderStatus("PENDING");
        OtpCode saved = otpCodeRepository.save(otp);

        SmsDeliveryResult delivery = smsService.sendOtp(phone, code);
        saved.setProviderStatus(delivery.status().name());
        saved.setProviderMessageId(delivery.providerMessageId());
        saved.setLastErrorCode(delivery.errorCode());
    }

    @Transactional
    public void verifyOtp(String phone, String code, OtpPurpose purpose) {
        OtpAttemptService.VerificationResult result =
                otpAttemptService.verify(phone, code, purpose, maxAttempts);
        switch (result) {
            case SUCCESS -> {
                return;
            }
            case EXPIRED -> throw new UnauthorizedException(
                    "Код истёк",
                    "error.otp.expired"
            );
            case ATTEMPTS_EXHAUSTED -> throw new TooManyRequestsException(
                    "Превышено количество попыток ввода кода"
            );
            case NOT_FOUND, INVALID -> throw new UnauthorizedException(
                    "Неверный код",
                    "error.otp.invalid"
            );
        }
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
