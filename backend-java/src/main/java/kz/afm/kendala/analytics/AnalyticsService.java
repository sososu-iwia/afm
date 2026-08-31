package kz.afm.kendala.analytics;

import kz.afm.kendala.analytics.dto.AnalyticsSummaryResponse;
import kz.afm.kendala.analytics.dto.ApplicationTrendResponse;
import kz.afm.kendala.analytics.dto.DecisionRateResponse;
import kz.afm.kendala.analytics.dto.ProcessingTimeResponse;
import kz.afm.kendala.analytics.dto.RegionMetricResponse;
import kz.afm.kendala.analytics.dto.RiskCategoryMetricResponse;
import kz.afm.kendala.analytics.dto.StatusMetricResponse;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.PaginationPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private static final int MAX_PAGE_SIZE = 100;
    private final AnalyticsRepository repository;

    public AnalyticsService(AnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(AnalyticsFilter filter) {
        return repository.summary(filter);
    }

    @Transactional(readOnly = true)
    public PageResponse<StatusMetricResponse> byStatus(
            AnalyticsFilter filter, int page, int size
    ) {
        PageSettings settings = page(page, size);
        return response(repository.byStatus(
                filter, settings.offset(), settings.size()), settings);
    }

    @Transactional(readOnly = true)
    public PageResponse<RegionMetricResponse> byRegion(
            AnalyticsFilter filter, int page, int size
    ) {
        PageSettings settings = page(page, size);
        return response(repository.byRegion(
                filter, settings.offset(), settings.size()), settings);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationTrendResponse> trend(
            AnalyticsFilter filter, int page, int size
    ) {
        PageSettings settings = page(page, size);
        return response(repository.trend(
                filter, settings.offset(), settings.size()), settings);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProcessingTimeResponse> processingTime(
            AnalyticsFilter filter, int page, int size
    ) {
        PageSettings settings = page(page, size);
        return response(repository.processingTime(
                filter, settings.offset(), settings.size()), settings);
    }

    @Transactional(readOnly = true)
    public PageResponse<RiskCategoryMetricResponse> riskCategories(
            AnalyticsFilter filter, int page, int size
    ) {
        PageSettings settings = page(page, size);
        return response(repository.riskCategories(
                filter, settings.offset(), settings.size()), settings);
    }

    @Transactional(readOnly = true)
    public DecisionRateResponse decisionRates(AnalyticsFilter filter) {
        return repository.decisionRates(filter);
    }

    private PageSettings page(int requestedPage, int requestedSize) {
        int page = PaginationPolicy.oneBased(requestedPage);
        int size = Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        return new PageSettings(page, size, (page - 1) * size);
    }

    private <T> PageResponse<T> response(
            AnalyticsRepository.PageSlice<T> slice, PageSettings settings
    ) {
        int totalPages = slice.totalElements() == 0
                ? 0
                : (int) Math.ceil((double) slice.totalElements() / settings.size());
        return new PageResponse<>(
                slice.content(),
                settings.page(),
                settings.size(),
                slice.totalElements(),
                totalPages
        );
    }

    private record PageSettings(int page, int size, int offset) {
    }
}
