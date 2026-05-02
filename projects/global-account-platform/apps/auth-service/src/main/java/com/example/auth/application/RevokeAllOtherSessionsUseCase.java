package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.result.RevokeOthersResult;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bulk-revoke all device sessions for the caller's account except the one identified by
 * the access token's {@code device_id} claim.
 *
 * <p>Spec: specs/contracts/http/auth-api.md DELETE /api/accounts/me/sessions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeAllOtherSessionsUseCase {

    private static final String ACTOR_TYPE_USER = "USER";

    private final DeviceSessionRepository deviceSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthEventPublisher authEventPublisher;

    @Transactional
    public RevokeOthersResult execute(String accountId, String currentDeviceId) {
        if (currentDeviceId == null || currentDeviceId.isBlank()) {
            throw new SessionNotFoundException();
        }
        // The caller's own session must exist and be active to anchor the exclusion.
        DeviceSession current = deviceSessionRepository.findByDeviceId(currentDeviceId)
                .filter(s -> s.getAccountId().equals(accountId) && s.isActive())
                .orElseThrow(SessionNotFoundException::new);

        // TASK-BE-248 Phase 2b: resolve tenantId from the caller's current session refresh token.
        // The current device's active JTI is the authoritative source for tenant context.
        String tenantId = refreshTokenRepository.findActiveJtisByDeviceId(currentDeviceId)
                .stream()
                .findFirst()
                .flatMap(refreshTokenRepository::findByJti)
                .map(RefreshToken::getTenantId)
                .filter(t -> t != null && !t.isBlank())
                .orElse(TenantContext.DEFAULT_TENANT_ID);

        List<DeviceSession> active = deviceSessionRepository.findActiveByAccountId(accountId);
        Instant now = Instant.now();
        int revokedCount = 0;
        for (DeviceSession session : active) {
            if (session.getDeviceId().equals(current.getDeviceId())) {
                continue;
            }
            List<String> revokedJtis =
                    refreshTokenRepository.findActiveJtisByDeviceId(session.getDeviceId());
            refreshTokenRepository.revokeAllByDeviceId(session.getDeviceId());
            session.revoke(now, RevokeReason.LOGOUT_OTHERS);
            deviceSessionRepository.save(session);
            authEventPublisher.publishAuthSessionRevoked(
                    accountId, tenantId, session.getDeviceId(),
                    RevokeReason.LOGOUT_OTHERS.name(),
                    revokedJtis, now,
                    ACTOR_TYPE_USER, accountId);
            revokedCount++;
        }
        log.info("Bulk-revoked other device sessions: account={}, currentDeviceId={}, revokedCount={}",
                accountId, currentDeviceId, revokedCount);
        return new RevokeOthersResult(revokedCount);
    }
}
