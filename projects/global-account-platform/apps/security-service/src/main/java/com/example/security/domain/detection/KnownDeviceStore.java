package com.example.security.domain.detection;

/**
 * Port for the per-account known-device set (Redis SET, TTL 90 days).
 */
public interface KnownDeviceStore {

    /** True if {@code deviceFingerprint} is in the account's known-device set. */
    boolean isKnown(String accountId, String deviceFingerprint);

    /** Add {@code deviceFingerprint} to the account's known-device set. */
    void remember(String accountId, String deviceFingerprint);
}
