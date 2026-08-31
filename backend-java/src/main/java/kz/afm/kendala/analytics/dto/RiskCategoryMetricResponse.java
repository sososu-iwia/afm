package kz.afm.kendala.analytics.dto;

public record RiskCategoryMetricResponse(
        String riskCategory,
        long applicationCount
) {
}
