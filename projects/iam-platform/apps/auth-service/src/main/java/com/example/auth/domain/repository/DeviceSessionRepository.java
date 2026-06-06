package com.example.auth.domain.repository;

import com.example.auth.domain.session.DeviceSession;

import java.util.List;
import java.util.Optional;

/**
 * Port for {@link DeviceSession} persistence. Implementation lives in infrastructure.
 *
 * <p>Spec: specs/services/auth-service/device-session.md.
 */
public interface DeviceSessionRepository {

    DeviceSession save(DeviceSession session);

    Optional<DeviceSession> findByDeviceId(String deviceId);

    /** Find an active (revoked_at IS NULL) session by (account, fingerprint). Used for upsert. */
    Optional<DeviceSession> findActiveByAccountAndFingerprint(String accountId, String fingerprint);

    /** All active sessions for an account, ordered by last_seen_at DESC (most recent first). */
    List<DeviceSession> findActiveByAccountId(String accountId);

    /** Count active sessions for an account. Used to check the concurrent-session limit. */
    long countActiveByAccountId(String accountId);

    /**
     * Returns the {@code limit} oldest active sessions for the account (by last_seen_at ASC).
     * Used by {@code EnforceConcurrentLimitUseCase} to choose eviction victims.
     */
    List<DeviceSession> findOldestActiveByAccountId(String accountId, int limit);
}
