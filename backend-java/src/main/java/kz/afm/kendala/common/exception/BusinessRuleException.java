package kz.afm.kendala.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.CONFLICT, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String message, String messageKey, Object... arguments) {
        super(
                HttpStatus.CONFLICT,
                "BUSINESS_RULE_VIOLATION",
                message,
                messageKey,
                arguments
        );
    }
}
