package kz.afm.kendala.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;
import kz.afm.kendala.application.entity.Application;

public record AiDuplicateApplication(
        @JsonProperty("application_id") UUID applicationId,
        @JsonProperty("iin_bin") String iinBin,
        String region,
        @JsonProperty("product_type") String productType,
        @JsonProperty("land_area") BigDecimal landArea,
        @JsonProperty("requested_amount") BigDecimal requestedAmount,
        String status
) {
    public static AiDuplicateApplication from(Application application) {
        return new AiDuplicateApplication(
                application.getId(),
                application.getIinOrBin(),
                application.getRegion(),
                application.getProductionType(),
                application.getLandArea(),
                application.getRequestedAmount(),
                application.getStatus().name().toLowerCase()
        );
    }
}
