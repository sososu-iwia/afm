package kz.afm.kendala.config;

import kz.afm.kendala.filestore.StorageService;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("storage")
public class StorageHealthIndicator implements HealthIndicator {

    private final StorageService storageService;

    public StorageHealthIndicator(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public Health health() {
        try {
            storageService.checkHealth();
            return Health.up().build();
        } catch (RuntimeException ex) {
            return Health.down().withDetail("error", "Storage is unavailable").build();
        }
    }
}

