package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.token.RefreshToken;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TASK-BE-607: was length = 36 — Flyway V0014 widened the jti/rotated_from COLUMNS to
    // VARCHAR(255) (TASK-MONO-046-1 Cluster A) to hold SAS's ~128-char refresh token values,
    // but this entity's declared length was never updated to match. ddl-auto=validate (the
    // Testcontainers/Flyway path) does not fail on a length mismatch, so this was invisible
    // there; a Hibernate-generated schema (H2 slice, ddl-auto=create-drop) creates the column
    // at the entity's declared length and an INSERT of a SAS-length token fails.
    @Column(name = "jti", nullable = false, unique = true, length = 255)
    private String jti;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    // TASK-BE-229: tenant_id for cross-tenant rotation prevention.
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // TASK-BE-607: was length = 36 — see the jti field comment above (V0014 widened this
    // column to VARCHAR(255) too, for symmetry with jti).
    @Column(name = "rotated_from", length = 255)
    private String rotatedFrom;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    /** Deprecated — superseded by {@link #deviceId}. Retained for D5 shadow-write window. */
    @Deprecated
    @Column(name = "device_fingerprint", length = 128)
    private String deviceFingerprint;

    /** Logical reference to {@code device_sessions.device_id}. Nullable for legacy rows. */
    @Column(name = "device_id", length = 36)
    private String deviceId;

    public RefreshToken toDomain() {
        return new RefreshToken(id, jti, accountId,
                tenantId != null ? tenantId : "fan-platform",
                issuedAt, expiresAt,
                rotatedFrom, revoked, deviceFingerprint, deviceId);
    }

    public static RefreshTokenJpaEntity fromDomain(RefreshToken token) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.id = token.getId();
        entity.jti = token.getJti();
        entity.accountId = token.getAccountId();
        entity.tenantId = token.getTenantId();
        entity.issuedAt = token.getIssuedAt();
        entity.expiresAt = token.getExpiresAt();
        entity.rotatedFrom = token.getRotatedFrom();
        entity.revoked = token.isRevoked();
        entity.deviceFingerprint = token.getDeviceFingerprint();
        entity.deviceId = token.getDeviceId();
        return entity;
    }
}
