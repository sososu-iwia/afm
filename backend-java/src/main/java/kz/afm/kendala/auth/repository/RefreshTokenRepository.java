package kz.afm.kendala.auth.repository;

import java.util.Optional;
import java.util.UUID;
import kz.afm.kendala.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = CURRENT_TIMESTAMP,
                r.revokeReason = :reason
            WHERE r.user.id = :userId
              AND r.revokedAt IS NULL
            """)
    void revokeAllForUser(UUID userId, String reason);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = CURRENT_TIMESTAMP,
                r.revokeReason = :reason
            WHERE r.familyId = :familyId
              AND r.revokedAt IS NULL
            """)
    void revokeFamily(UUID familyId, String reason);

    @Query("""
            SELECT r
            FROM RefreshToken r
            JOIN FETCH r.user
            WHERE r.user.id = :userId
              AND r.revokedAt IS NULL
              AND r.expiresAt > CURRENT_TIMESTAMP
            ORDER BY r.issuedAt DESC
            """)
    java.util.List<RefreshToken> findActiveSessionsByUserId(UUID userId);

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = CURRENT_TIMESTAMP,
                r.revokeReason = :reason
            WHERE r.user.id = :userId
              AND r.id = :sessionId
              AND r.revokedAt IS NULL
            """)
    int revokeUserSession(UUID userId, UUID sessionId, String reason);

    @Deprecated
    default void revokeAllForUser(UUID userId) {
        revokeAllForUser(userId, "LOGOUT_ALL");
    }
}
