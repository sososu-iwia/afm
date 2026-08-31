package kz.afm.kendala.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Profile;

class LocalFileSmsSenderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writesMaskedDevOtpWithoutPhoneInFileName() throws Exception {
        LocalFileSmsSender sender = new LocalFileSmsSender(tempDirectory.toString());

        SmsDeliveryResult result = sender.send(
                "+77000000001",
                "Код подтверждения: 482901. Не сообщайте его никому.");

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.DELIVERED_LOCAL);
        Path file = Files.list(tempDirectory).findFirst().orElseThrow();
        assertThat(file.getFileName().toString()).doesNotContain("77000000001");
        assertThat(Files.readString(file))
                .contains("recipient=+77******01")
                .contains("otp=482901")
                .contains("delivery=LOCAL_DEVELOPMENT_ONLY");
    }

    @Test
    void isRestrictedToDevProfile() {
        Profile profile = LocalFileSmsSender.class.getAnnotation(Profile.class);
        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("dev");
    }
}
