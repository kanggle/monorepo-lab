package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.exception.SessionOwnershipMismatchException;
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
 * Revoke a single device session by deviceId. Cascades to refresh_tokens via the
 * application layer (no DB-level ON DELETE CASCADE — see V0003 migration comment).
 *
 * <p>Spec: specs/contracts/http/auth-api.md DELETE /api/accounts/me/sessions/{deviceId}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokeSessionUseCase {

    private static final String ACTOR_TYPE_USER = "USER";

    private final DeviceSessionRepository deviceSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthEventPublisher authEventPublisher;

    @Transactional
    public void execute(String callerAccountId, String deviceId) {
        DeviceSession session = deviceSessionRepository.findByDeviceId(deviceId)
                .orElseThrow(SessionNotFoundException::new);
        if (!session.getAccountId().equals(callerAccountId)) {
            throw new SessionOwnershipMismatchException();
        }
        if (session.isRevoked()) {
            // Idempotent — already revoked is treated as not-found per the contract.
            throw new SessionNotFoundException();
        }

        Instant now = Instant.now();
        List<String> revokedJtis = refreshTokenRepository.findActiveJtisByDeviceId(deviceId);

        // TASK-BE-248 Phase 2b: resolve tenantId from the device's active refresh token
        // (authoritative source). Falls back to DEFAULT_TENANT_ID for legacy rows without tenant.
        String tenantId = revokedJtis.stream()
                .findFirst()
                .flatMap(refreshTokenRepository::findByJti)
                .map(RefreshToken::getTenantId)
                .filter(t -> t != null && !t.isBlank())
                .orElse(TenantContext.DEFAULT_TENANT_ID);

        refreshTokenRepository.revokeAllByDeviceId(deviceId);

        session.revoke(now, RevokeReason.USER_REQUESTED);
        deviceSessionRepository.save(session);

        authEventPublisher.publishAuthSessionRevoked(
                callerAccountId, tenantId, deviceId,
                RevokeReason.USER_REQUESTED.name(),
                revokedJtis, now,
                ACTOR_TYPE_USER, callerAccountId);
        log.info("Device session revoked by user: account={}, deviceId={}", callerAccountId, deviceId);
    }
}
