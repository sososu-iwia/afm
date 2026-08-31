package kz.afm.kendala.ai;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationScoreRepository extends JpaRepository<ApplicationScore, UUID> {
}
