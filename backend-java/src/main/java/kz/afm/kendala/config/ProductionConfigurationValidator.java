package kz.afm.kendala.config;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {

    private final Environment environment;

    public ProductionConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validateJwtSecret(require("app.jwt.secret"));
        String databaseUrl = require("spring.datasource.url");
        forbidLocalhost("spring.datasource.url", databaseUrl);
        require("spring.datasource.username");
        require("spring.datasource.password");
        validateServiceUri("app.ai.base-url", false);
        forbidLocalhost("app.ai.base-url", require("app.ai.base-url"));
        require("app.ai.internal-api-key");
        require("app.pdf.unicode-font-path");
        if (!"true".equalsIgnoreCase(require("app.notifications.enabled"))) {
            throw new IllegalStateException("Production notifications must be enabled");
        }
        require("app.notifications.email-from");
        require("spring.mail.host");
        if (environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true)) {
            require("spring.mail.username");
            require("spring.mail.password");
        }

        String smsProvider = require("app.sms.provider");
        if ("local-file".equals(smsProvider)) {
            throw new IllegalStateException("APP_SMS_PROVIDER=local-file is forbidden in production");
        }
        if (!"smsc".equals(smsProvider)) {
            throw new IllegalStateException("Production requires APP_SMS_PROVIDER=smsc");
        }
        require("SMSC_LOGIN");
        require("SMSC_PASSWORD");

        String storageProvider = require("app.storage.provider");
        if ("supabase".equals(storageProvider)) {
            List.of("SUPABASE_URL", "SUPABASE_STORAGE_BUCKET", "SUPABASE_SERVICE_KEY")
                    .forEach(this::require);
            validateServiceUri("SUPABASE_URL", true);
        } else if ("local".equals(storageProvider)) {
            require("app.storage.local.path");
        } else {
            throw new IllegalStateException("Unsupported production storage provider");
        }

        String origins = require("app.cors.allowed-origins");
        if (origins.contains("*")) {
            throw new IllegalStateException("Wildcard CORS origin is forbidden in production");
        }
        for (String origin : origins.split(",")) {
            validateUri("app.cors.allowed-origins", origin.strip(), false);
        }
    }

    private String require(String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required production configuration is missing: " + key);
        }
        return value;
    }

    private void validateServiceUri(String key, boolean requireHttps) {
        validateUri(key, require(key), requireHttps);
    }

    private void validateUri(String key, String value, boolean requireHttps) {
        try {
            URI uri = URI.create(value);
            boolean allowedScheme = "https".equalsIgnoreCase(uri.getScheme())
                    || (!requireHttps && "http".equalsIgnoreCase(uri.getScheme()));
            if (!allowedScheme
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsafe production URI configuration: " + key);
        }
    }

    private void validateJwtSecret(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_JWT_SECRET must be base64 and at least 32 bytes in production");
        }
    }

    private void forbidLocalhost(String key, String value) {
        String lower = value.toLowerCase();
        if (lower.contains("localhost") || lower.contains("127.0.0.1")) {
            throw new IllegalStateException("Localhost is forbidden in production configuration: " + key);
        }
    }
}
