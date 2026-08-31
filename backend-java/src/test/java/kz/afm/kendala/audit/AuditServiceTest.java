package kz.afm.kendala.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final kz.afm.kendala.application.repository.UserRepository userRepository =
            mock(kz.afm.kendala.application.repository.UserRepository.class);
    private final AuditService service = new AuditService(repository, new ObjectMapper(), userRepository);

    @Test
    void metadataSanitizerRedactsSensitiveValues() {
        service.record(
                "anonymous",
                "ANONYMOUS",
                "LOGIN_FAILED",
                "AUTH",
                "-",
                null,
                Map.of(
                        "phone", "+77000000000",
                        "refreshToken", "secret-refresh",
                        "safe", "visible"
                ),
                "127.0.0.1",
                "test-correlation"
        );

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMetadata())
                .contains("[REDACTED]")
                .contains("visible")
                .doesNotContain("+77000000000")
                .doesNotContain("secret-refresh");
    }
}
