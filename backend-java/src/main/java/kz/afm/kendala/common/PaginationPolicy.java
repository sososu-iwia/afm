package kz.afm.kendala.common;

import kz.afm.kendala.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class PaginationPolicy {

    public static final int MAX_PAGE_NUMBER = 10_000;

    private PaginationPolicy() {
    }

    public static int oneBased(int requestedPage) {
        int page = Math.max(requestedPage, 1);
        if (page > MAX_PAGE_NUMBER) {
            throw invalid();
        }
        return page;
    }

    public static int zeroBased(int requestedPage) {
        int page = Math.max(requestedPage, 0);
        if (page >= MAX_PAGE_NUMBER) {
            throw invalid();
        }
        return page;
    }

    private static ApiException invalid() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PAGE",
                "Номер страницы превышает допустимый предел"
        );
    }
}
