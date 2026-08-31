package kz.afm.kendala.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import kz.afm.kendala.auth.entity.AuthRateLimitEvent;
import kz.afm.kendala.auth.repository.AuthRateLimitEventRepository;
import kz.afm.kendala.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AuthRateLimitServiceTest {

    @Test
    void rejectsWhenPhoneLimitReached() {
        AuthRateLimitEventRepository repository = Mockito.mock(AuthRateLimitEventRepository.class);
        when(repository.countByKeyTypeAndKeyHashAndCreatedAtAfter(eq("PHONE"), any(), any(Instant.class)))
                .thenReturn(2L);
        AuthRateLimitService service = new AuthRateLimitService(repository, 2, 30, 900);

        assertThatThrownBy(() -> service.checkAndRecord("+77001234567", "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsWhenIpLimitReached() {
        AuthRateLimitEventRepository repository = Mockito.mock(AuthRateLimitEventRepository.class);
        when(repository.countByKeyTypeAndKeyHashAndCreatedAtAfter(eq("PHONE"), any(), any(Instant.class)))
                .thenReturn(0L);
        when(repository.countByKeyTypeAndKeyHashAndCreatedAtAfter(eq("IP"), any(), any(Instant.class)))
                .thenReturn(3L);
        AuthRateLimitService service = new AuthRateLimitService(repository, 10, 3, 900);

        assertThatThrownBy(() -> service.checkAndRecord("+77001234567", "127.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void recordsHashedPhoneAndIpWithoutRawValues() {
        AuthRateLimitEventRepository repository = Mockito.mock(AuthRateLimitEventRepository.class);
        AuthRateLimitService service = new AuthRateLimitService(repository, 10, 30, 900);

        service.checkAndRecord("+77001234567", "127.0.0.1");

        verify(repository, Mockito.times(2)).save(any(AuthRateLimitEvent.class));
    }
}
