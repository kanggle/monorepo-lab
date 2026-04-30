package com.example.admin.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per operator refresh JWT. The token itself is self-contained
 * (signature + exp), so only the {@code jti} is tracked here for revocation
 * and reuse detection (see security.md §Session Lifecycle).
 */
@Entity
@Table(name = "admin_operator_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOperatorRefreshTokenJpaEntity {

    public static final String REASON_LOGOUT = "LOGOUT";
    public static final String REASON_ROTATED = "ROTATED";
    public static final String REASON_REUSE_DETECTED = "REUSE_DETECTED";
    public static final String REASON_FORCE_LOGOUT = "FORCE_LOGOUT";

    @Id
    @Column(name = "jti", length = 36, nullable = false)
    private String jti;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_from", length = 36)
    private String rotatedFrom;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 64)
    private String revokeReason;

    public static AdminOperatorRefreshTokenJpaEntity issue(String jti,
                                                           Long operatorId,
                                                           Instant issuedAt,
                                                           Instant expiresAt,
                                                           String rotatedFrom) {
        AdminOperatorRefreshTokenJpaEntity e = new AdminOperatorRefreshTokenJpaEntity();
        e.jti = jti;
        e.operatorId = operatorId;
        e.issuedAt = issuedAt;
        e.expiresAt = expiresAt;
        e.rotatedFrom = rotatedFrom;
        return e;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant at, String reason) {
        this.revokedAt = at;
        this.revokeReason = reason;
    }
}
