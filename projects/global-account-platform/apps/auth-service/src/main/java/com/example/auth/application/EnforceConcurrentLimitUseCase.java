package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Concurrent-session policy enforcement.
 *
 * <p>Spec: specs/services/auth-service/device-session.md D4. If the active session count
 * for an account exceeds {@code auth.device-session.max-active-sessions} (env
 * {@code AUTH_MAX_ACTIVE_SESSIONS}, default 10), the oldest active sessions are revoked
 * with reason {@link RevokeReason#EVICTED_BY_LIMIT}, their refresh tokens are
 * cascade-revoked, and one {@code auth.session.revoked} event is emitted per evicted
 * device. Runs inside the caller's transaction so an eviction failure rolls back the
 * login that triggered it (D4 atomicity requirement).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnforceConcurrentLimitUseCase {

    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";

    private final DeviceSessionRepository deviceSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthEventPublisher authEventPublisher;

    @Value("${auth.device-session.max-active-sessions:10}")
    private int maxActiveSessions;

    /**
     * Evicts the oldest active sessions for the account so the post-condition is that
     * at most {@code maxActiveSessions - 1} sessions remain (the caller is about to
     * insert one more).
     *
     * <p>TASK-BE-248 Phase 2b: {@code tenantId} is now required so that the
     * {@code auth.session.revoked} event payload carries the correct tenant context.
     *
     * @param tenantId the tenant that owns the account (required, non-blank)
     * @return device_ids that were evicted. Empty list if none.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> enforce(String accountId, String tenantId) {
        long activeCount = deviceSessionRepository.countActiveByAccountId(accountId);
        // Caller will insert one new session, so we must end with <= max - 1 active.
        long target = (long) maxActiveSessions - 1;
        if (activeCount <= target) {
            return List.of();
        }
        int evictCount = (int) (activeCount - target);
        List<DeviceSession> victims =
                deviceSessionRepository.findOldestActiveByAccountId(accountId, evictCount);

        Instant now = Instant.now();
        List<String> evictedIds = new ArrayList<>(victims.size());
        for (DeviceSession victim : victims) {
            victim.revoke(now, RevokeReason.EVICTED_BY_LIMIT);
            deviceSessionRepository.save(victim);

            List<String> revokedJtis =
                    refreshTokenRepository.findActiveJtisByDeviceId(victim.getDeviceId());
            refreshTokenRepository.revokeAllByDeviceId(victim.getDeviceId());

            authEventPublisher.publishAuthSessionRevoked(
                    accountId,
                    tenantId,
                    victim.getDeviceId(),
                    RevokeReason.EVICTED_BY_LIMIT.name(),
                    revokedJtis,
                    now,
                    ACTOR_TYPE_SYSTEM,
                    null);
            evictedIds.add(victim.getDeviceId());
            log.info("Evicted device session due to concurrent-session limit: account={}, deviceId={}",
                    accountId, victim.getDeviceId());
        }
        return evictedIds;
    }

    int getMaxActiveSessions() {
        return maxActiveSessions;
    }
}
