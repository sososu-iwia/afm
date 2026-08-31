package kz.afm.kendala.auth.service;

import kz.afm.kendala.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PhoneNormalizer {

    public String normalize(String raw) {
        if (raw == null) {
            throw invalid("Телефон не может быть пустым", "validation.phone-required");
        }
        String digits = raw.strip().replaceAll("[^0-9]", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() != 11 || !digits.startsWith("7")) {
            throw invalid(
                    "Неверный формат казахстанского номера телефона",
                    "validation.phone-format"
            );
        }
        return "+" + digits;
    }

    private ApiException invalid(String message, String messageKey) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                messageKey
        );
    }
}
