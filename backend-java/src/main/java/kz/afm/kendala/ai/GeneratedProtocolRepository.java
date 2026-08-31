package kz.afm.kendala.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedProtocolRepository extends JpaRepository<GeneratedProtocol, UUID> {
    List<GeneratedProtocol> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    java.util.Optional<GeneratedProtocol> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
