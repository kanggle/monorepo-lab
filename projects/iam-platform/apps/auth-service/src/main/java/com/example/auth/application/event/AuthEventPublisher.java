package com.example.auth.application.event;

import com.example.auth.domain.session.SessionContext;

import java.time.Instant;
import java.util.List;

/**
 * Port for auth-service event publishing (TASK-BE-450 — outbox v1 → v2).
 *
 * <p>Previously a concrete {@code BaseEventPublisher} subclass that wrote to the
 * shared lib {@code outbox} table via {@code OutboxWriter}. It is now a port; the
 * implementation {@link com.example.auth.infrastructure.outbox.OutboxAuthEventPublisher}
 * builds the canonical 7-field envelope (byte-identical to the v1
 * {@code BaseEventPublisher.writeEvent} wire shape) and persists an
 * {@code auth_outbox} row driven by the v2 {@code AbstractOutboxPublisher} relay.
 *
 * <p>Every method signature is preserved verbatim from the v1 concrete class so
 * no call site (LoginUseCase, RefreshTokenUseCase, RevokeSessionUseCase, …)
 * changes, and the tenantId-required guards are re-enforced in the impl.
 */
public interface AuthEventPublisher {

    void publishLoginAttempted(String accountId, String emailHash, String tenantId,
                               SessionContext ctx);

    void publishLoginFailed(String accountId, String emailHash, String tenantId,
                            String failureReason, int failCount, SessionContext ctx);

    /**
     * Legacy 3-arg form kept for integration tests and any pre-TASK-BE-025 call site.
     */
    void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx);

    /**
     * Extended form (TASK-BE-025 + TASK-BE-229): carries {@code deviceId}, {@code isNewDevice}.
     */
    void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx,
                               String deviceId, Boolean isNewDevice);

    /**
     * Full form with tenantId (TASK-BE-229 / TASK-BE-248). tenantId is required.
     */
    void publishLoginSucceeded(String accountId, String sessionJti, String tenantId,
                               SessionContext ctx, String deviceId, Boolean isNewDevice);

    /**
     * Extended form carrying {@code loginMethod} for OAuth social logins (legacy — no tenantId).
     *
     * @deprecated Use {@link #publishLoginSucceeded(String, String, String, SessionContext, String, Boolean, String)}.
     */
    @Deprecated
    void publishLoginSucceeded(String accountId, String sessionJti, SessionContext ctx,
                               String deviceId, Boolean isNewDevice, String loginMethod);

    /**
     * Full form with tenantId and loginMethod for OAuth social logins (TASK-BE-248).
     * tenantId is required.
     */
    void publishLoginSucceeded(String accountId, String sessionJti, String tenantId,
                               SessionContext ctx, String deviceId, Boolean isNewDevice,
                               String loginMethod);

    /**
     * Publishes auth.token.refreshed with tenantId (TASK-BE-229 / TASK-BE-248).
     * tenantId is required.
     */
    void publishTokenRefreshed(String accountId, String tenantId,
                               String previousJti, String newJti, SessionContext ctx);

    /**
     * Publishes auth.token.reuse.detected (TASK-BE-259). tenantId is required.
     */
    void publishTokenReuseDetected(String accountId, String tenantId, String reusedJti,
                                   Instant originalRotationAt, Instant reuseAttemptAt,
                                   String ipMasked, String deviceFingerprint,
                                   boolean sessionsRevoked, int revokedCount);

    /**
     * Publishes auth.token.tenant.mismatch security event (TASK-BE-229).
     */
    void publishTokenTenantMismatch(String accountId, String submittedTenantId,
                                    String expectedTenantId, String jti,
                                    String ipMasked, String deviceFingerprint);

    /**
     * Publishes {@code auth.session.created} when a new device session is registered on
     * login. tenantId is required (TASK-BE-248).
     */
    void publishAuthSessionCreated(String accountId, String tenantId, String deviceId,
                                   String sessionJti,
                                   String deviceFingerprintHash, String userAgentFamily,
                                   String ipMasked, String geoCountry, Instant issuedAt,
                                   List<String> evictedDeviceIds);

    /**
     * Publishes {@code auth.session.revoked} for a single device session
     * (TASK-BE-022). tenantId is required (TASK-BE-248).
     */
    void publishAuthSessionRevoked(String accountId, String tenantId, String deviceId,
                                   String reason,
                                   List<String> revokedJtis, Instant revokedAt,
                                   String actorType, String actorAccountId);
}
