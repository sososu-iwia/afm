package kz.afm.kendala.application.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import kz.afm.kendala.application.entity.Document;
import kz.afm.kendala.application.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByApplicationId(UUID applicationId);

    List<Document> findByApplicationIdIn(Collection<UUID> applicationIds);

    boolean existsByApplicationIdAndDocumentType(UUID applicationId, DocumentType documentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    java.util.Optional<Document> findLockedById(UUID id);
}
