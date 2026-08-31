package kz.afm.kendala.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import kz.afm.kendala.auth.entity.AuthRateLimitEvent;
import kz.afm.kendala.auth.repository.AuthRateLimitEventRepository;
import kz.afm.kendala.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class AuthRateLimitService {

    private final AuthRateLimitEventRepository repository;
    private final int phoneLimit;
    private final int ipLimit;
    private final long windowSeconds;

    public AuthRateLimitService(
            AuthRateLimitEventRepository repository,
            @Value("${app.auth-rate-limit.phone-max:10}") int phoneLimit,
            @Value("${app.auth-rate-limit.ip-max:30}") int ipLimit,
            @Value("${app.auth-rate-limit.window-seconds:900}") long windowSeconds
    ) {
        this.repository = repository;
        this.phoneLimit = phoneLimit;
        this.ipLimit = ipLimit;
        this.windowSeconds = windowSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecord(String normalizedPhone, String remoteAddress) {
        Instant since = Instant.now().minusSeconds(windowSeconds);
        String phoneHash = sha256(normalizedPhone);
        String ipHash = sha256(remoteAddress == null ? "unknown" : remoteAddress);

        if (repository.countByKeyTypeAndKeyHashAndCreatedAtAfter("PHONE", phoneHash, since) >= phoneLimit
                || repository.countByKeyTypeAndKeyHashAndCreatedAtAfter("IP", ipHash, since) >= ipLimit) {
            throw new TooManyRequestsException("Слишком много запросов. Повторите попытку позже");
        }

        repository.save(event("PHONE", phoneHash));
        repository.save(event("IP", ipHash));
    }

    private AuthRateLimitEvent event(String type, String hash) {
        AuthRateLimitEvent event = new AuthRateLimitEvent();
        event.setKeyType(type);
        event.setKeyHash(hash);
        return event;
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
