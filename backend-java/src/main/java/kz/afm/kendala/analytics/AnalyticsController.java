package kz.afm.kendala.analytics;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import kz.afm.kendala.analytics.dto.AnalyticsSummaryResponse;
import kz.afm.kendala.analytics.dto.ApplicationTrendResponse;
import kz.afm.kendala.analytics.dto.DecisionRateResponse;
import kz.afm.kendala.analytics.dto.ProcessingTimeResponse;
import kz.afm.kendala.analytics.dto.RegionMetricResponse;
import kz.afm.kendala.analytics.dto.RiskCategoryMetricResponse;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.analytics.dto.StatusMetricResponse;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.common.dto.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAnyRole('ADMIN','CHAIRMAN','SECRETARY')")
@Tag(name = "Analytics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.summary(filter(dateFrom, dateTo, region, status, productionType, activityType));
    }

    @GetMapping("/applications-by-status")
    public PageResponse<StatusMetricResponse> byStatus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.byStatus(
                filter(dateFrom, dateTo, region, status, productionType, activityType), page, size);
    }

    @GetMapping("/applications-by-region")
    public PageResponse<RegionMetricResponse> byRegion(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.byRegion(
                filter(dateFrom, dateTo, region, status, productionType, activityType), page, size);
    }

    @GetMapping("/application-trend")
    public PageResponse<ApplicationTrendResponse> trend(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.trend(
                filter(dateFrom, dateTo, region, status, productionType, activityType), page, size);
    }

    @GetMapping("/processing-time")
    public PageResponse<ProcessingTimeResponse> processingTime(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.processingTime(
                filter(dateFrom, dateTo, region, status, productionType, activityType), page, size);
    }

    @GetMapping("/risk-categories")
    public PageResponse<RiskCategoryMetricResponse> riskCategories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.riskCategories(
                filter(dateFrom, dateTo, region, status, productionType, activityType), page, size);
    }

    @GetMapping("/approval-rate")
    public DecisionRateResponse approvalRate(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String region,
            @RequestParam(required = false)
            @Size(max = 128, message = "{validation.max-length}") String productionType,
            @RequestParam(required = false) ActivityType activityType
    ) {
        return service.decisionRates(
                filter(dateFrom, dateTo, region, null, productionType, activityType));
    }

    private AnalyticsFilter filter(
            LocalDate dateFrom,
            LocalDate dateTo,
            String region,
            ApplicationStatus status,
            String productionType,
            ActivityType activityType
    ) {
        return AnalyticsFilter.of(dateFrom, dateTo, region, status, productionType, activityType);
    }
}
