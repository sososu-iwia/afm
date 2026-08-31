package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public ServiceUnavailableException(
            String code,
            String message,
            String messageKey,
            Object... arguments
    ) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message, messageKey, arguments);
    }
}
