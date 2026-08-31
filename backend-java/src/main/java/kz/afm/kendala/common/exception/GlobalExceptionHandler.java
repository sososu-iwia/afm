package kz.afm.kendala.common.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import kz.afm.kendala.common.dto.ErrorResponse;
import kz.afm.kendala.common.i18n.ApiMessageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiMessageResolver messages;

    public GlobalExceptionHandler() {
        this(new ApiMessageResolver());
    }

    @Autowired
    public GlobalExceptionHandler(ApiMessageResolver messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApplicationIncompleteException.class)
    public ResponseEntity<ErrorResponse> handleIncomplete(ApplicationIncompleteException ex) {
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(
                Instant.now(), 422, "APPLICATION_INCOMPLETE",
                messages.message(
                        "error.application.incomplete",
                        "Заявка не является полной"
                ),
                Map.of(), ex.getMissingFields(), ex.getMissingDocuments()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied() {
        return build(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                messages.message("error.access-denied", "Доступ запрещён"),
                Map.of()
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        return build(
                ex.getStatus(),
                ex.getCode(),
                messages.message(
                        ex.getMessageKey(),
                        ex.getMessage(),
                        ex.getMessageArguments()
                ),
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                messages.message("error.validation", "Некорректные данные"),
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> fieldErrors.put(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        ));
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                messages.message("error.validation", "Некорректные данные"),
                fieldErrors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch() {
        return build(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                messages.message("error.bad-request", "Некорректный формат параметра"),
                Map.of()
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidRequest() {
        return build(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                messages.message("error.bad-request", "Некорректный формат параметра"),
                Map.of()
        );
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMultipart() {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                messages.message(
                        "error.file-too-large",
                        "Превышен максимальный размер файла"
                ),
                Map.of()
        );
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                messages.message(
                        "error.commission-conflict",
                        "Заявка уже была изменена другим пользователем"
                ),
                Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound() {
        return build(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                messages.message("error.not-found", "Ресурс не найден"),
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                messages.message("error.internal", "Внутренняя ошибка сервера"),
                Map.of()
        );
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                fieldErrors
        ));
    }
}
