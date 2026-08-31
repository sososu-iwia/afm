package kz.afm.kendala.application.repository;

import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, UUID> {

    List<ApplicationStatusHistory> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    void deleteByApplicationId(UUID applicationId);
}
