package com.example.auth.domain.session;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate representing an account-scoped device session.
 *
 * <p>Spec: specs/services/auth-service/device-session.md.
 *
 * <p>Pure POJO — no JPA / Spring annotations.
 */
public class DeviceSession {

    /** Sentinel stored in {@code device_fingerprint} when the observation is null/blank (D3). */
    public static final String UNKNOWN_FINGERPRINT = "unknown";

    private final Long id;
    private final String deviceId;
    private final String accountId;
    private final String deviceFingerprint;
    private final String userAgent;
    private String ipLast;
    private String geoLast;
    private final Instant issuedAt;
    private Instant lastSeenAt;
    private Instant revokedAt;
    private RevokeReason revokeReason;

    public DeviceSession(Long id, String deviceId, String accountId, String deviceFingerprint,
                         String userAgent, String ipLast, String geoLast,
                         Instant issuedAt, Instant lastSeenAt,
                         Instant revokedAt, RevokeReason revokeReason) {
        this.id = id;
        this.deviceId = Objects.requireNonNull(deviceId, "deviceId must not be null");
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.deviceFingerprint = Objects.requireNonNull(deviceFingerprint,
                "deviceFingerprint must not be null (use UNKNOWN_FINGERPRINT sentinel)");
        this.userAgent = userAgent;
        this.ipLast = ipLast;
        this.geoLast = geoLast;
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt must not be null");
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
    }

    /**
     * Factory for a brand-new session row (no DB id yet). {@code lastSeenAt} starts equal
     * to {@code issuedAt}; not yet revoked.
     */
    public static DeviceSession create(String deviceId, String accountId, String deviceFingerprint,
                                       String userAgent, String ipAddress, String geoCountry,
                                       Instant issuedAt) {
        String fp = (deviceFingerprint == null || deviceFingerprint.isBlank())
                ? UNKNOWN_FINGERPRINT : deviceFingerprint;
        return new DeviceSession(
                null, deviceId, accountId, fp,
                userAgent, ipAddress, geoCountry,
                issuedAt, issuedAt, null, null);
    }

    /** Marks this session as last-seen at the given instant. No-op if already revoked. */
    public void touch(Instant now, String ipAddress, String geoCountry) {
        if (isRevoked()) {
            return;
        }
        this.lastSeenAt = Objects.requireNonNull(now, "now must not be null");
        if (ipAddress != null) {
            this.ipLast = ipAddress;
        }
        if (geoCountry != null) {
            this.geoLast = geoCountry;
        }
    }

    /** Revokes this session with the given reason. Idempotent — first call wins. */
    public void revoke(Instant now, RevokeReason reason) {
        if (isRevoked()) {
            return;
        }
        this.revokedAt = Objects.requireNonNull(now, "now must not be null");
        this.revokeReason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getAccountId() { return accountId; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getUserAgent() { return userAgent; }
    public String getIpLast() { return ipLast; }
    public String getGeoLast() { return geoLast; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public RevokeReason getRevokeReason() { return revokeReason; }
}
