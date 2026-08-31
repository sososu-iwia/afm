package kz.afm.kendala.auth.repository;

import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.auth.entity.OtpCode;
import kz.afm.kendala.auth.enums.OtpPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {

    Optional<OtpCode> findTopByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpCode> findFirstByPhoneAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phone, OtpPurpose purpose);

    @Modifying
    @Query("""
            UPDATE OtpCode o
            SET o.consumedAt = CURRENT_TIMESTAMP,
                o.invalidatedAt = CURRENT_TIMESTAMP
            WHERE o.phone = :phone
              AND o.purpose = :purpose
              AND o.consumedAt IS NULL
              AND o.invalidatedAt IS NULL
            """)
    void invalidateActiveOtps(String phone, OtpPurpose purpose);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OtpCode o SET o.attempts = o.attempts + 1 WHERE o.id = :id")
    void incrementAttempts(@Param("id") UUID id);
}
