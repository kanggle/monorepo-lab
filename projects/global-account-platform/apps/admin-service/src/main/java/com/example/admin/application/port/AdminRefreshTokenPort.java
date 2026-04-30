package com.example.admin.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * TASK-BE-040-fix — port over the {@code admin_operator_refresh_tokens}
 * registry. Keeps JPA types out of the application layer so
 * {@link com.example.admin.application.AdminRefreshTokenService} /
 * {@link com.example.admin.application.AdminLogoutService} /
 * {@link com.example.admin.application.AdminRefreshTokenIssuer} depend only on
 * abstractions (architecture.md Allowed Dependencies).
 *
 * <p>Method surface is deliberately narrow — only what the three services use.
 * The {@link TokenRecord} projection exposes the fields needed for rotation /
 * reuse detection / logout revocation without leaking
 * {@code AdminOperatorRefreshTokenJpaEntity}.
 */
public interface AdminRefreshTokenPort {

    /** Revocation reason: operator self-initiated logout. */
    String REASON_LOGOUT = "LOGOUT";
    /** Revocation reason: replaced by a freshly rotated token. */
    String REASON_ROTATED = "ROTATED";
    /** Revocation reason: rotated jti replayed — chain-wide bulk revoke. */
    String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    /** Revocation reason: operator force-logout performed by admin. */
    String REASON_FORCE_LOGOUT = "FORCE_LOGOUT";

    /** @return the registry row for {@code jti}, or empty when unknown. */
    Optional<TokenRecord> findByJti(String jti);

    /**
     * Persists a freshly-issued row. Caller provides all fields — implementation
     * does not mutate dates.
     */
    void insert(NewTokenRecord row);

    /** Marks an existing jti as revoked with the given reason (normal rotation / self-logout). */
    void revoke(String jti, Instant at, String reason);

    /**
     * Bulk-revokes every non-revoked refresh token for {@code operatorInternalId}.
     * Used on rotated-token reuse detection.
     *
     * @return number of rows updated.
     */
    int revokeAllForOperator(Long operatorInternalId, Instant at, String reason);

    /**
     * Immutable projection of a registry row. Contains only fields the
     * application layer cares about.
     */
    record TokenRecord(
            String jti,
            Long operatorInternalId,
            Instant issuedAt,
            Instant expiresAt,
            String rotatedFrom,
            Instant revokedAt,
            String revokeReason
    ) {
        public boolean isRevoked() {
            return revokedAt != null;
        }
    }

    /**
     * Value used to INSERT a new row. Mirrors {@link TokenRecord} minus the
     * revocation fields (always null at insert time).
     */
    record NewTokenRecord(
            String jti,
            Long operatorInternalId,
            Instant issuedAt,
            Instant expiresAt,
            String rotatedFrom
    ) {}
}
