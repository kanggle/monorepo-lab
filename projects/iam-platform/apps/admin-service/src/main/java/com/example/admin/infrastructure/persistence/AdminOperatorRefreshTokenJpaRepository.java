package com.example.admin.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AdminOperatorRefreshTokenJpaRepository
        extends JpaRepository<AdminOperatorRefreshTokenJpaEntity, String> {

    /**
     * Bulk-revokes every non-revoked refresh token for {@code operatorId}.
     * Used on rotated-token reuse detection (specs/services/admin-service/security.md
     * §Session Lifecycle).
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AdminOperatorRefreshTokenJpaEntity t " +
            "SET t.revokedAt = :at, t.revokeReason = :reason " +
            "WHERE t.operatorId = :operatorId AND t.revokedAt IS NULL")
    int revokeAllForOperator(@Param("operatorId") Long operatorId,
                             @Param("at") Instant at,
                             @Param("reason") String reason);
}
