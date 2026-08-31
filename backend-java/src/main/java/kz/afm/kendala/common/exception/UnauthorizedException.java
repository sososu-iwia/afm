package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public UnauthorizedException(String message, String messageKey, Object... arguments) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message, messageKey, arguments);
    }
}
