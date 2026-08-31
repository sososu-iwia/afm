package kz.afm.kendala.auth.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "local-file")
public class LocalFileSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSmsSender.class);
    private static final Pattern OTP_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path directory;

    public LocalFileSmsSender(
            @Value("${app.sms.local-file.directory:.runtime/dev-sms}") String directory
    ) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public SmsDeliveryResult send(String to, String message) {
        Instant createdAt = Instant.now();
        String correlationId = safeCorrelationId(MDC.get("correlationId"));
        String deliveryId = UUID.randomUUID().toString();
        String fileName = FILE_TIME.format(createdAt) + "-" + correlationId + "-"
                + recipientFingerprint(to) + ".sms.txt";
        String content = "delivery=LOCAL_DEVELOPMENT_ONLY\n"
                + "recipient=" + maskPhone(to) + "\n"
                + "purpose=OTP_VERIFICATION\n"
                + "otp=" + extractOtp(message) + "\n"
                + "createdAt=" + createdAt + "\n"
                + "correlationId=" + correlationId + "\n"
                + "deliveryId=" + deliveryId + "\n";
        try {
            Files.createDirectories(directory);
            Files.writeString(
                    directory.resolve(fileName),
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            return SmsDeliveryResult.deliveredLocal(deliveryId);
        } catch (IOException exception) {
            log.error("Local development SMS file could not be written to configured directory");
            return SmsDeliveryResult.failed("LOCAL_SMS_WRITE_FAILED");
        }
    }

    Path getDirectory() {
        return directory;
    }

    private String extractOtp(String message) {
        Matcher matcher = OTP_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? matcher.group(1) : "UNAVAILABLE";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 3) + "******" + phone.substring(phone.length() - 2);
    }

    private String recipientFingerprint(String recipient) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((recipient == null ? "" : recipient).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 5);
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private String safeCorrelationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
        String safe = value.replaceAll("[^A-Za-z0-9_-]", "");
        return safe.isBlank() ? UUID.randomUUID().toString().substring(0, 8)
                : safe.substring(0, Math.min(safe.length(), 24));
    }
}
