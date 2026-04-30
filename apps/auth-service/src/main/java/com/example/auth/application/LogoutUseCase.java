package com.example.auth.application;

import com.example.auth.application.command.LogoutCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.TokenParseException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.repository.TokenBlacklist;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.infrastructure.redis.RedisTokenBlacklist;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private static final String ACTOR_TYPE_USER = "USER";

    private final TokenGeneratorPort tokenGeneratorPort;
    private final TokenBlacklist tokenBlacklist;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final AuthEventPublisher authEventPublisher;

    @Transactional
    public void execute(LogoutCommand command) {
        String jti;
        String accountId;
        try {
            jti = tokenGeneratorPort.extractJti(command.refreshToken());
            accountId = tokenGeneratorPort.extractAccountId(command.refreshToken());
        } catch (TokenParseException e) {
            log.warn("Failed to parse refresh token during logout: {}", e.getMessage());
            return; // graceful - token may already be invalid
        }

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByJti(jti);
        if (tokenOpt.isEmpty()) {
            return;
        }
        RefreshToken token = tokenOpt.get();

        // Use tenant_id from DB row (authoritative) for Redis key (TASK-BE-229).
        String tenantId = token.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.DEFAULT_TENANT_ID;
        }

        long remainingTtl = token.getExpiresAt().getEpochSecond() - Instant.now().getEpochSecond();
        if (remainingTtl > 0) {
            blacklist(tenantId, jti, remainingTtl);
        }
        token.revoke();
        refreshTokenRepository.save(token);

        Instant revokedAt = Instant.now();

        // Resolve the caller's device_id
        String deviceId = command.deviceId();
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = token.getDeviceId();
        }

        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Logout without device_id (legacy token, jti={}, account={}); skipping session revoke",
                    jti, accountId);
            return;
        }

        Optional<DeviceSession> sessionOpt = deviceSessionRepository.findByDeviceId(deviceId);
        if (sessionOpt.isEmpty() || sessionOpt.get().isRevoked()) {
            log.info("Logout: device_session absent or already revoked; skipping session event: deviceId={}",
                    deviceId);
            return;
        }
        if (!sessionOpt.get().getAccountId().equals(accountId)) {
            log.warn("Logout: device_session account mismatch; skipping. deviceId={}, session.account={}, token.account={}",
                    deviceId, sessionOpt.get().getAccountId(), accountId);
            return;
        }

        DeviceSession session = sessionOpt.get();
        session.revoke(revokedAt, RevokeReason.USER_REQUESTED);
        deviceSessionRepository.save(session);

        authEventPublisher.publishAuthSessionRevoked(
                accountId,
                deviceId,
                RevokeReason.USER_REQUESTED.name(),
                List.of(jti),
                revokedAt,
                ACTOR_TYPE_USER,
                accountId
        );
    }

    private void blacklist(String tenantId, String jti, long ttlSeconds) {
        if (tokenBlacklist instanceof RedisTokenBlacklist tenantAware) {
            tenantAware.blacklist(tenantId, jti, ttlSeconds);
        } else {
            tokenBlacklist.blacklist(jti, ttlSeconds);
        }
    }
}
