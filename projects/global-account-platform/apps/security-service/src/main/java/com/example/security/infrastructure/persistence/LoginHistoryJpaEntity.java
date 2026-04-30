package com.example.security.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "login_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TASK-BE-248: tenant_id is the leading column on the per-account indexes
    // (idx_login_history_tenant_account / _outcome) — see V0008 migration.
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "event_id", nullable = false, unique = true)
    private String eventId;

    @Column(name = "account_id")
    private String accountId;

    @Column(name = "outcome", nullable = false, length = 30)
    private String outcome;

    @Column(name = "ip_masked", length = 45)
    private String ipMasked;

    @Column(name = "user_agent_family", length = 100)
    private String userAgentFamily;

    @Column(name = "device_fingerprint", length = 128)
    private String deviceFingerprint;

    @Column(name = "geo_country", length = 10)
    private String geoCountry;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public static LoginHistoryJpaEntity from(String tenantId, String eventId, String accountId, String outcome,
                                              String ipMasked, String userAgentFamily,
                                              String deviceFingerprint, String geoCountry,
                                              Instant occurredAt) {
        LoginHistoryJpaEntity entity = new LoginHistoryJpaEntity();
        entity.tenantId = tenantId;
        entity.eventId = eventId;
        entity.accountId = accountId;
        entity.outcome = outcome;
        entity.ipMasked = ipMasked;
        entity.userAgentFamily = userAgentFamily;
        entity.deviceFingerprint = deviceFingerprint;
        entity.geoCountry = geoCountry;
        entity.occurredAt = occurredAt;
        return entity;
    }
}
