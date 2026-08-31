package kz.afm.kendala.application.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.application.entity.AdditionalDocumentRequest;
import kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdditionalDocumentRequestRepository
        extends JpaRepository<AdditionalDocumentRequest, UUID> {

    List<AdditionalDocumentRequest> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    Optional<AdditionalDocumentRequest> findFirstByApplicationIdAndStatusOrderByCreatedAtDesc(
            UUID applicationId,
            AdditionalDocumentRequestStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from AdditionalDocumentRequest request
            where request.application.id = :applicationId
              and request.status = kz.afm.kendala.application.enums.AdditionalDocumentRequestStatus.OPEN
            """)
    Optional<AdditionalDocumentRequest> findOpenLockedByApplicationId(
            @Param("applicationId") UUID applicationId
    );
}
