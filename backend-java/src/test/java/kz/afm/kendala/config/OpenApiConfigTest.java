package kz.afm.kendala.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

class OpenApiConfigTest {

    @Test
    void openApiContainsBearerAuthAndMainTags() {
        var api = new OpenApiConfig().kenDalaOpenApi();

        var scheme = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(scheme.getScheme()).isEqualTo("bearer");
        assertThat(scheme.getBearerFormat()).isEqualTo("JWT");

        Set<String> tags = api.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toSet());
        assertThat(tags).contains(
                "Authentication", "Applications", "Documents", "Application Processing",
                "Commission", "Analytics", "Public Registry", "User Administration",
                "Audit", "AI Jobs", "Health");
    }

    @Test
    void productionValidatorRejectsLocalFileSms() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.jwt.secret", "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2g=")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/kendala")
                .withProperty("spring.datasource.username", "kendala")
                .withProperty("spring.datasource.password", "secret")
                .withProperty("app.ai.base-url", "https://ai.internal")
                .withProperty("app.ai.internal-api-key", "secret")
                .withProperty("app.pdf.unicode-font-path", "/fonts/unicode.ttf")
                .withProperty("app.notifications.enabled", "true")
                .withProperty("app.notifications.email-from", "no-reply@example.org")
                .withProperty("spring.mail.host", "mail.internal")
                .withProperty("spring.mail.username", "mailer")
                .withProperty("spring.mail.password", "secret")
                .withProperty("app.sms.provider", "local-file");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                new ProductionConfigurationValidator(environment)
                        .run(new DefaultApplicationArguments(new String[0]))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local-file is forbidden");
    }
}
