package kz.afm.kendala.ai.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AiScoreRequest(
        UUID applicationId,
        String iinOrBin,
        String region,
        String productionType,
        BigDecimal landArea,
        BigDecimal requestedAmount,
        BigDecimal docCompleteness,
        BigDecimal creditHistoryYears,
        BigDecimal previousLoansCount,
        BigDecimal previousLoansRepaid,
        Boolean taxDebt
) {
    public AiScoreRequest(
            UUID applicationId,
            String iinOrBin,
            String region,
            String productionType,
            BigDecimal landArea,
            BigDecimal requestedAmount,
            BigDecimal docCompleteness
    ) {
        this(applicationId, iinOrBin, region, productionType, landArea, requestedAmount,
                docCompleteness, null, null, null, null);
    }
}
