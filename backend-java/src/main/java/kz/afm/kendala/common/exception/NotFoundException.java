package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public NotFoundException(String message, String messageKey, Object... arguments) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message, messageKey, arguments);
    }
}
