package kz.afm.kendala.stage1;

import static org.assertj.core.api.Assertions.assertThat;

import kz.afm.kendala.common.exception.GlobalExceptionHandler;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionReturnsExpectedStatus() {
        ResponseEntity<?> response = handler.handleApiException(new NotFoundException("not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unauthorizedExceptionReturns401() {
        ResponseEntity<?> response = handler.handleApiException(new UnauthorizedException("no auth"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unexpectedExceptionReturns500() {
        ResponseEntity<?> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
