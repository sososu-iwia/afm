package kz.afm.kendala.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kz.afm.kendala.application.repository.ApplicationRepository;
import kz.afm.kendala.application.service.ApplicationNumberGenerator;
import org.junit.jupiter.api.Test;

class ApplicationNumberGeneratorTest {

    @Test
    void usesDatabaseSequenceForDistinctConcurrencySafeCandidates() {
        ApplicationRepository repository = mock(ApplicationRepository.class);
        when(repository.nextApplicationNumberSequence()).thenReturn(100000L, 100001L);
        ApplicationNumberGenerator generator = new ApplicationNumberGenerator(repository);

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("KD2-\\d{8}-100000");
        assertThat(second).matches("KD2-\\d{8}-100001");
        assertThat(first).isNotEqualTo(second);
    }
}
