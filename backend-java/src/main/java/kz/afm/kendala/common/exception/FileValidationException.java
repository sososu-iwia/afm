package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class FileValidationException extends ApiException {

    public FileValidationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
