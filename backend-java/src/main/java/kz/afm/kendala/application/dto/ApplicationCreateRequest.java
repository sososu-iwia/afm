package kz.afm.kendala.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicantCategory;

public record ApplicationCreateRequest(
        @Pattern(regexp = "\\d{12}", message = "{validation.iin-or-bin}")
        String iinOrBin,

        @Size(max = 128, message = "{validation.region-size}")
        String region,

        @Size(max = 128, message = "{validation.production-type-size}")
        String productionType,

        @DecimalMin(value = "0.01", message = "{validation.land-area-positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.land-area-digits}")
        BigDecimal landArea,

        @DecimalMin(value = "0.01", message = "{validation.amount-positive}")
        @Digits(integer = 17, fraction = 2, message = "{validation.amount-digits}")
        BigDecimal requestedAmount,

        ActivityType activityType,

        ApplicantCategory applicantCategory
) {
    public ApplicationCreateRequest(
            String iinOrBin,
            String region,
            String productionType,
            BigDecimal landArea,
            BigDecimal requestedAmount
    ) {
        this(iinOrBin, region, productionType, landArea, requestedAmount, null, null);
    }
}
