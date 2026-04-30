package com.example.auth.domain.token;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a refresh token.
 * Pure POJO - no framework annotations.
 */
public class RefreshToken {

    private final Long id;
    private final String jti;
    private final String accountId;
    /** Tenant identifier. Must not be null (fail-closed per multi-tenancy spec). */
    private final String tenantId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String rotatedFrom;
    private boolean revoked;
    /** @deprecated superseded by {@link #deviceId}; retained for shadow-write window (D5). */
    @Deprecated
    private final String deviceFingerprint;
    /** Logical reference to {@code device_sessions.device_id}. Nullable for legacy rows. */
    private final String deviceId;

    public RefreshToken(Long id, String jti, String accountId, String tenantId,
                        Instant issuedAt, Instant expiresAt,
                        String rotatedFrom, boolean revoked, String deviceFingerprint, String deviceId) {
        this.id = id;
        this.jti = Objects.requireNonNull(jti, "jti must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.rotatedFrom = rotatedFrom;
        this.revoked = revoked;
        this.deviceFingerprint = deviceFingerprint;
        this.deviceId = deviceId;
    }

    /**
     * @deprecated Use the constructor that carries {@code tenantId}.
     *             Retained for backwards compatibility; defaults tenantId to "fan-platform".
     */
    @Deprecated
    public RefreshToken(Long id, String jti, String accountId, Instant issuedAt, Instant expiresAt,
                        String rotatedFrom, boolean revoked, String deviceFingerprint, String deviceId) {
        this(id, jti, accountId, "fan-platform", issuedAt, expiresAt, rotatedFrom, revoked,
                deviceFingerprint, deviceId);
    }

    /**
     * @deprecated Use {@link #RefreshToken(Long, String, String, String, Instant, Instant,
     *             String, boolean, String, String)} that carries {@code deviceId}.
     *             Retained for backwards compatibility during TASK-BE-023 rollout.
     */
    @Deprecated
    public RefreshToken(Long id, String jti, String accountId, Instant issuedAt, Instant expiresAt,
                        String rotatedFrom, boolean revoked, String deviceFingerprint) {
        this(id, jti, accountId, "fan-platform", issuedAt, expiresAt, rotatedFrom, revoked,
                deviceFingerprint, null);
    }

    public static RefreshToken create(String jti, String accountId, String tenantId,
                                       Instant issuedAt, Instant expiresAt, String rotatedFrom,
                                       String deviceFingerprint, String deviceId) {
        return new RefreshToken(null, jti, accountId, tenantId, issuedAt, expiresAt, rotatedFrom,
                false, deviceFingerprint, deviceId);
    }

    /**
     * @deprecated Use {@link #create(String, String, String, Instant, Instant, String, String, String)}
     *             which requires tenantId. Retained for callers that have not yet adopted
     *             tenant-aware token creation; defaults to "fan-platform".
     */
    @Deprecated
    public static RefreshToken create(String jti, String accountId, Instant issuedAt,
                                       Instant expiresAt, String rotatedFrom,
                                       String deviceFingerprint, String deviceId) {
        return create(jti, accountId, "fan-platform", issuedAt, expiresAt, rotatedFrom,
                deviceFingerprint, deviceId);
    }

    /**
     * @deprecated retained for callers that have not yet adopted device_session integration.
     */
    @Deprecated
    public static RefreshToken create(String jti, String accountId, Instant issuedAt,
                                       Instant expiresAt, String rotatedFrom, String deviceFingerprint) {
        return create(jti, accountId, "fan-platform", issuedAt, expiresAt, rotatedFrom,
                deviceFingerprint, null);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void revoke() {
        this.revoked = true;
    }

    public Long getId() { return id; }
    public String getJti() { return jti; }
    public String getAccountId() { return accountId; }
    public String getTenantId() { return tenantId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRotatedFrom() { return rotatedFrom; }
    @Deprecated
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getDeviceId() { return deviceId; }
}
