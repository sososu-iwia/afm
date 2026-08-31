package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String message, String messageKey, Object... arguments) {
        super(HttpStatus.CONFLICT, "CONFLICT", message, messageKey, arguments);
    }
}
