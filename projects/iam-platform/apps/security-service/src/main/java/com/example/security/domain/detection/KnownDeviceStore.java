package com.example.security.domain.detection;

/**
 * Port for the per-tenant per-account known-device set (Redis SET, TTL 90 days).
 *
 * <p>TASK-BE-248 Phase 2a: all operations require {@code tenantId} so that device
 * registrations are isolated per tenant. The Redis key format is
 * {@code security:device:known:{tenantId}:{accountId}}.
 *
 * <p>A device seen in tenantA never suppresses the DEVICE_CHANGE alert in tenantB
 * for the same account, preventing cross-tenant false-negative leakage.</p>
 */
public interface KnownDeviceStore {

    /**
     * True if {@code deviceFingerprint} is in the tenant/account's known-device set.
     *
     * @param tenantId          tenant identifier (must be non-null/non-blank)
     * @param accountId         account identifier
     * @param deviceFingerprint device fingerprint to check
     */
    boolean isKnown(String tenantId, String accountId, String deviceFingerprint);

    /**
     * Add {@code deviceFingerprint} to the tenant/account's known-device set.
     *
     * @param tenantId          tenant identifier (must be non-null/non-blank)
     * @param accountId         account identifier
     * @param deviceFingerprint device fingerprint to register
     */
    void remember(String tenantId, String accountId, String deviceFingerprint);
}
