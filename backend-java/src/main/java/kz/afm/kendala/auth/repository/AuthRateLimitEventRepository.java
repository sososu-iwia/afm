package kz.afm.kendala.auth.repository;

import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.auth.entity.AuthRateLimitEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRateLimitEventRepository extends JpaRepository<AuthRateLimitEvent, UUID> {

    long countByKeyTypeAndKeyHashAndCreatedAtAfter(String keyType, String keyHash, Instant since);

    void deleteByCreatedAtBefore(Instant before);
}

