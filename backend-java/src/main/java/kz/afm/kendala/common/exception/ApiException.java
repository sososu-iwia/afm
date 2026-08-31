package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final Object[] messageArguments;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    public ApiException(
            HttpStatus status,
            String code,
            String message,
            String messageKey,
            Object... messageArguments
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.messageArguments = messageArguments == null
                ? new Object[0]
                : messageArguments.clone();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getMessageArguments() {
        return messageArguments.clone();
    }
}
