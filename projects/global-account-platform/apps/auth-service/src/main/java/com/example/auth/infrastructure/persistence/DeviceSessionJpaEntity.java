package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persistence representation of {@link DeviceSession}. JPA annotations live here — the
 * domain aggregate stays framework-free.
 */
@Entity
@Table(name = "device_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceSessionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true, length = 36)
    private String deviceId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "device_fingerprint", nullable = false, length = 128)
    private String deviceFingerprint;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_last", length = 45)
    private String ipLast;

    @Column(name = "geo_last", length = 2)
    private String geoLast;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 40)
    private RevokeReason revokeReason;

    public static DeviceSessionJpaEntity fromDomain(DeviceSession s) {
        DeviceSessionJpaEntity e = new DeviceSessionJpaEntity();
        e.id = s.getId();
        e.deviceId = s.getDeviceId();
        e.accountId = s.getAccountId();
        e.deviceFingerprint = s.getDeviceFingerprint();
        e.userAgent = s.getUserAgent();
        e.ipLast = s.getIpLast();
        e.geoLast = s.getGeoLast();
        e.issuedAt = s.getIssuedAt();
        e.lastSeenAt = s.getLastSeenAt();
        e.revokedAt = s.getRevokedAt();
        e.revokeReason = s.getRevokeReason();
        return e;
    }

    /**
     * Copy mutable fields from the domain aggregate into this managed entity. Identity
     * (id, device_id, account_id, fingerprint, issued_at) is preserved so JPA managed
     * state stays consistent across save() round-trips. Mirrors the version-preservation
     * pattern used by membership-service's SubscriptionRepositoryAdapter.
     */
    public void updateFromDomain(DeviceSession s) {
        this.userAgent = s.getUserAgent();
        this.ipLast = s.getIpLast();
        this.geoLast = s.getGeoLast();
        this.lastSeenAt = s.getLastSeenAt();
        this.revokedAt = s.getRevokedAt();
        this.revokeReason = s.getRevokeReason();
    }

    public DeviceSession toDomain() {
        return new DeviceSession(
                id, deviceId, accountId, deviceFingerprint,
                userAgent, ipLast, geoLast,
                issuedAt, lastSeenAt, revokedAt, revokeReason);
    }
}
