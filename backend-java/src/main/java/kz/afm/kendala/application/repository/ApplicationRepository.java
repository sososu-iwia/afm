package kz.afm.kendala.application.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.enums.ApplicationStatus;
import kz.afm.kendala.application.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.LockModeType;

public interface ApplicationRepository extends JpaRepository<Application, UUID>,
        JpaSpecificationExecutor<Application> {

    Page<Application> findByApplicantId(UUID applicantId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "applicant")
    Page<Application> findAll(Specification<Application> specification, Pageable pageable);

    Optional<Application> findByIdAndApplicantId(UUID id, UUID applicantId);

    boolean existsByApplicationNumber(String applicationNumber);

    Optional<Application> findByApplicationNumber(String applicationNumber);

    @Query(value = "SELECT nextval('application_number_seq')", nativeQuery = true)
    long nextApplicationNumberSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findLockedById(UUID id);

    @Query("""
            select a from Application a
            where a.id <> :applicationId
              and a.status in :statuses
              and (
                    a.iinOrBin = :iinOrBin
                    or (a.region = :region and a.productionType = :productionType)
              )
            """)
    List<Application> findDuplicateCandidates(
            UUID applicationId,
            String iinOrBin,
            String region,
            String productionType,
            Collection<ApplicationStatus> statuses
    );
}
