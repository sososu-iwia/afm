package kz.afm.kendala.application.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import kz.afm.kendala.application.repository.ApplicationRepository;
import org.springframework.stereotype.Component;

@Component
public class ApplicationNumberGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final ApplicationRepository applicationRepository;

    public ApplicationNumberGenerator(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    public String generate() {
        String datePart = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            long suffix = applicationRepository.nextApplicationNumberSequence();
            String number = "KD2-" + datePart + "-" + String.format("%06d", suffix);
            if (!applicationRepository.existsByApplicationNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Не удалось сформировать уникальный номер заявки");
    }
}
