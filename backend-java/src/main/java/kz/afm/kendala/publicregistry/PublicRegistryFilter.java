package kz.afm.kendala.publicregistry;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import kz.afm.kendala.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public record PublicRegistryFilter(
        String region,
        String productionType,
        Instant dateFrom,
        Instant dateToExclusive,
        BigDecimal minAmount,
        BigDecimal maxAmount
) {
    public static PublicRegistryFilter of(
            String region,
            String productionType,
            LocalDate dateFrom,
            LocalDate dateTo,
            BigDecimal minAmount,
            BigDecimal maxAmount
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw invalid("dateFrom не может быть позже dateTo");
        }
        if (minAmount != null && minAmount.signum() < 0) {
            throw invalid("minAmount не может быть отрицательным");
        }
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw invalid("maxAmount не может быть отрицательным");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw invalid("minAmount не может быть больше maxAmount");
        }
        return new PublicRegistryFilter(
                normalize(region),
                normalize(productionType),
                dateFrom == null ? null : dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                dateTo == null ? null : dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                minAmount,
                maxAmount
        );
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", message);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}

