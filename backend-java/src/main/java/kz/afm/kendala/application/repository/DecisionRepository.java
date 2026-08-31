package kz.afm.kendala.application.repository;

import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.entity.Decision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    List<Decision> findByApplicationId(UUID applicationId);

    List<Decision> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
