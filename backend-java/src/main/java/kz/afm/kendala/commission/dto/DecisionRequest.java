package kz.afm.kendala.commission.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import kz.afm.kendala.application.enums.DocumentType;

public record DecisionRequest(
        @Size(min = 3, max = 2000, message = "{validation.decision-reason-size}")
        String reason,

        @DecimalMin(value = "0.01", message = "{validation.approved-amount-positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.approved-amount-digits}")
        BigDecimal approvedAmount,

        @Size(max = 20, message = "Список типов документов слишком большой")
        List<DocumentType> documentTypes
) {
    public DecisionRequest(String reason) {
        this(reason, null, List.of());
    }

    public DecisionRequest(String reason, BigDecimal approvedAmount) {
        this(reason, approvedAmount, List.of());
    }
}
