package kz.afm.kendala.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import kz.afm.kendala.application.enums.ActivityType;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public record AnalyticsFilter(
        Instant dateFrom,
        Instant dateToExclusive,
        String region,
        ApplicationStatus status,
        String productionType,
        ActivityType activityType
) {
    public static AnalyticsFilter of(
            LocalDate dateFrom,
            LocalDate dateTo,
            String region,
            ApplicationStatus status,
            String productionType,
            ActivityType activityType
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_FILTER",
                    "dateFrom не может быть позже dateTo"
            );
        }
        return new AnalyticsFilter(
                dateFrom == null ? null : dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                normalize(region),
                status,
                normalize(productionType),
                activityType
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
