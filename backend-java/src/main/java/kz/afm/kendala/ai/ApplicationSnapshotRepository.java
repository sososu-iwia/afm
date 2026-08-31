package kz.afm.kendala.ai;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSnapshotRepository extends JpaRepository<ApplicationSnapshot, UUID> {
    boolean existsByApplicationIdAndSubmissionRevision(UUID applicationId, int submissionRevision);
    Optional<ApplicationSnapshot> findByApplicationIdAndSubmissionRevision(UUID applicationId, int submissionRevision);
    List<ApplicationSnapshot> findByApplicationIdOrderBySubmissionRevisionAsc(UUID applicationId);
}
