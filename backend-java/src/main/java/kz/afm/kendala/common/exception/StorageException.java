package kz.afm.kendala.common.exception;

public class StorageException extends ApiException {

    public StorageException(String message) {
        super(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "STORAGE_UNAVAILABLE",
                message,
                "error.external-service"
        );
    }
}
