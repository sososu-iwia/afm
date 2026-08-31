package kz.afm.kendala.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/dev")
@Profile("dev")
public class DevController {

    private static final Pattern OTP_LINE = Pattern.compile("^otp=(.+)$", Pattern.MULTILINE);

    private final Path smsDir;

    public DevController(
            @Value("${app.sms.local-file.directory:.runtime/dev-sms}") String smsDir
    ) {
        this.smsDir = Path.of(smsDir).toAbsolutePath().normalize();
    }

    @GetMapping("/otp/{phone}")
    public Map<String, String> getLastOtp(@PathVariable String phone) {
        String fingerprint = recipientFingerprint(phone);
        String code = findLatestOtp(fingerprint);
        return Map.of("phone", phone, "code", code != null ? code : "not_found");
    }

    private String findLatestOtp(String fingerprint) {
        if (!Files.isDirectory(smsDir)) return null;
        try (Stream<Path> files = Files.list(smsDir)) {
            Optional<Path> latest = files
                    .filter(p -> p.getFileName().toString().contains(fingerprint))
                    .max(Comparator.comparing(p -> p.getFileName().toString()));
            if (latest.isEmpty()) return null;
            String content = Files.readString(latest.get(), StandardCharsets.UTF_8);
            Matcher m = OTP_LINE.matcher(content);
            return m.find() ? m.group(1) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private String recipientFingerprint(String phone) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((phone == null ? "" : phone).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 5);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
