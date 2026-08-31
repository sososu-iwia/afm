package kz.afm.kendala.common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

@Component
public class ApiMessageResolver {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");
    private static final Locale KAZAKH = Locale.forLanguageTag("kk");

    private final MessageSource messageSource;

    public ApiMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ApiMessageResolver() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        this.messageSource = source;
    }

    public String message(String key, String fallback, Object... arguments) {
        return message(key, fallback, normalize(
                org.springframework.context.i18n.LocaleContextHolder.getLocale()), arguments);
    }

    public String message(
            String key,
            String fallback,
            HttpServletRequest request,
            Object... arguments
    ) {
        return message(key, fallback, normalize(request.getLocale()), arguments);
    }

    private String message(
            String key,
            String fallback,
            Locale locale,
            Object... arguments
    ) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        return messageSource.getMessage(key, arguments, fallback, locale);
    }

    private Locale normalize(Locale locale) {
        return locale != null && "kk".equalsIgnoreCase(locale.getLanguage())
                ? KAZAKH
                : RUSSIAN;
    }
}

