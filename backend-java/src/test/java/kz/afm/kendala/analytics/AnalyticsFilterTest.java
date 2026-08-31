package kz.afm.kendala.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.common.exception.ApiException;
import org.junit.jupiter.api.Test;

class AnalyticsFilterTest {

    @Test
    void convertsInclusiveDateRangeToUtcHalfOpenInterval() {
        AnalyticsFilter filter = AnalyticsFilter.of(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                "  Akmola ",
                ApplicationStatus.APPROVED,
                " GRAIN ",
                null
        );

        assertThat(filter.dateFrom()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(filter.dateToExclusive()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
        assertThat(filter.region()).isEqualTo("Akmola");
        assertThat(filter.productionType()).isEqualTo("GRAIN");
    }

    @Test
    void rejectsInvertedDateRange() {
        assertThatThrownBy(() -> AnalyticsFilter.of(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                null,
                null
        ))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("dateFrom");
    }
}
