package com.example.auth.application;

import com.example.auth.application.command.RefreshTokenCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionRevokedException;
import com.example.auth.application.exception.TokenExpiredException;
import com.example.auth.application.exception.TokenParseException;
import com.example.auth.application.exception.TokenReuseDetectedException;
import com.example.auth.application.exception.TokenTenantMismatchException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.application.result.RefreshTokenResult;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.repository.TokenBlacklist;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenPair;
import com.example.auth.domain.token.TokenReuseDetector;
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
public class RefreshTokenUseCase {

    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";

    private final TokenGeneratorPort tokenGeneratorPort;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklist tokenBlacklist;
    private final TokenReuseDetector tokenReuseDetector;
    private final BulkInvalidationStore bulkInvalidationStore;
    private final AuthEventPublisher authEventPublisher;
    private final DeviceSessionRepository deviceSessionRepository;

    @Transactional
    public RefreshTokenResult execute(RefreshTokenCommand command) {
        SessionContext ctx = command.sessionContext();

        // Extract JTI, account ID, and tenant_id from the refresh token
        String jti;
        String accountId;
        String submittedTenantId;
        try {
            jti = tokenGeneratorPort.extractJti(command.refreshToken());
            accountId = tokenGeneratorPort.extractAccountId(command.refreshToken());
            submittedTenantId = tokenGeneratorPort.extractTenantId(command.refreshToken());
        } catch (TokenParseException e) {
            log.warn("Failed to parse refresh token: {}", e.getMessage());
            throw new TokenExpiredException();
        }

        // Fall back to "fan-platform" for legacy tokens without tenant_id claim
        if (submittedTenantId == null || submittedTenantId.isBlank()) {
            submittedTenantId = TenantContext.DEFAULT_TENANT_ID;
        }

        // Look up the refresh token in DB. Reuse detection must run BEFORE any revoked/
        // blacklisted/invalidate-all short-circuit so a replay of a rotated token still
        // triggers the incident-response path (TASK-BE-062 §B — security-first ordering).
        RefreshToken existingToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(TokenExpiredException::new);

        // Check for reuse FIRST (security-first ordering).
        if (tokenReuseDetector.isReuse(existingToken)) {
            handleReuseDetected(existingToken, jti, accountId, ctx);
            throw new TokenReuseDetectedException();
        }

        // TASK-BE-229: tenant_id validation — cross-tenant rotation is absolutely forbidden.
        // The DB row is the authoritative source; the JWT claim is validated against it.
        String dbTenantId = existingToken.getTenantId();
        if (!dbTenantId.equals(submittedTenantId)) {
            log.warn("TOKEN_TENANT_MISMATCH detected: submitted={} db={} jti={} account={}",
                    submittedTenantId, dbTenantId, jti, accountId);
            authEventPublisher.publishTokenTenantMismatch(
                    accountId, submittedTenantId, dbTenantId, jti,
                    ctx.ipMasked(), ctx.deviceFingerprint());
            throw new TokenTenantMismatchException(submittedTenantId, dbTenantId);
        }

        // Check blacklist (tenant-aware key, fail-closed: if Redis is down, deny refresh)
        if (isBlacklisted(dbTenantId, jti)) {
            throw new SessionRevokedException();
        }

        // Check bulk invalidation marker
        Optional<Instant> invalidatedAt = bulkInvalidationStore.getInvalidatedAt(accountId);
        if (invalidatedAt.isPresent()) {
            Instant tokenIat;
            try {
                tokenIat = tokenGeneratorPort.extractIssuedAt(command.refreshToken());
            } catch (TokenParseException e) {
                log.warn("Failed to extract iat for invalidate-all check, fail-closed: {}", e.getMessage());
                throw new SessionRevokedException();
            }
            if (tokenIat.isBefore(invalidatedAt.get())) {
                throw new SessionRevokedException();
            }
        }

        // Check if revoked
        if (existingToken.isRevoked()) {
            throw new SessionRevokedException();
        }

        // Check if expired
        if (existingToken.isExpired()) {
            throw new TokenExpiredException();
        }

        // Rotation: inherit the existing device_id.
        String deviceId = existingToken.getDeviceId();
        if (deviceId != null) {
            deviceSessionRepository.findByDeviceId(deviceId).ifPresent(session -> {
                if (session.isActive()) {
                    session.touch(Instant.now(), ctx.ipAddress(), ctx.resolvedGeoCountry());
                    deviceSessionRepository.save(session);
                }
            });
        }

        // Build tenant context from the DB row (authoritative)
        String tenantType = resolveTenantType(dbTenantId);
        TenantContext tenantContext = new TenantContext(dbTenantId, tenantType);

        TokenPair newTokenPair = tokenGeneratorPort.generateTokenPair(accountId, "user", deviceId,
                tenantContext);
        String newJti = tokenGeneratorPort.extractJti(newTokenPair.refreshToken());

        // Persist new refresh token with rotated_from pointer and same tenant_id
        Instant now = Instant.now();
        @SuppressWarnings("deprecation")
        String legacyFingerprint = existingToken.getDeviceFingerprint();
        RefreshToken newRefreshToken = RefreshToken.create(
                newJti, accountId, dbTenantId,
                now,
                now.plusSeconds(tokenGeneratorPort.refreshTokenTtlSeconds()),
                jti, legacyFingerprint, deviceId
        );
        refreshTokenRepository.save(newRefreshToken);

        // Blacklist the old refresh token (tenant-aware key)
        long remainingTtl = existingToken.getExpiresAt().getEpochSecond() - now.getEpochSecond();
        if (remainingTtl > 0) {
            blacklist(dbTenantId, jti, remainingTtl);
        }

        // Publish event with tenantId
        authEventPublisher.publishTokenRefreshed(accountId, dbTenantId, jti, newJti, ctx);

        return RefreshTokenResult.of(
                newTokenPair.accessToken(),
                newTokenPair.refreshToken(),
                newTokenPair.expiresIn()
        );
    }

    /**
     * Resolves the tenant type for a given tenantId.
     */
    private String resolveTenantType(String tenantId) {
        if ("fan-platform".equals(tenantId)) {
            return "B2C_CONSUMER";
        }
        return "B2B_ENTERPRISE";
    }

    /**
     * Tenant-aware blacklist check. Falls through to legacy key when BlacklistAdapter
     * supports the extended interface.
     */
    private boolean isBlacklisted(String tenantId, String jti) {
        if (tokenBlacklist instanceof RedisTokenBlacklist tenantAware) {
            return tenantAware.isBlacklisted(tenantId, jti);
        }
        return tokenBlacklist.isBlacklisted(jti);
    }

    /**
     * Tenant-aware blacklist write.
     */
    private void blacklist(String tenantId, String jti, long ttlSeconds) {
        if (tokenBlacklist instanceof RedisTokenBlacklist tenantAware) {
            tenantAware.blacklist(tenantId, jti, ttlSeconds);
        } else {
            tokenBlacklist.blacklist(jti, ttlSeconds);
        }
    }

    /**
     * Handles a detected refresh-token reuse: bulk-revokes the account's refresh tokens
     * and device sessions, sets the Redis invalidate-all marker, emits
     * {@code auth.token.reuse.detected} and one {@code auth.session.revoked} event per
     * affected device, and throws {@link TokenReuseDetectedException}.
     */
    private void handleReuseDetected(RefreshToken existingToken, String jti, String accountId,
                                     SessionContext ctx) {
        log.warn("Refresh token reuse detected for account={}, jti={}", accountId, jti);

        Instant originalRotationAt = refreshTokenRepository.findByRotatedFrom(jti)
                .map(RefreshToken::getIssuedAt)
                .orElse(null);

        boolean alreadyRevoked = existingToken.isRevoked();

        List<DeviceSession> activeSessions = deviceSessionRepository.findActiveByAccountId(accountId);
        java.util.Map<String, List<String>> jtisByDevice = new java.util.LinkedHashMap<>();
        for (DeviceSession session : activeSessions) {
            jtisByDevice.put(session.getDeviceId(),
                    refreshTokenRepository.findActiveJtisByDeviceId(session.getDeviceId()));
        }

        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);

        bulkInvalidationStore.invalidateAll(accountId, tokenGeneratorPort.refreshTokenTtlSeconds());

        if (alreadyRevoked && revokedCount == 0) {
            log.info("Token reuse on an already-revoked account, skipping duplicate event emission: account={}",
                    accountId);
            return;
        }

        Instant reuseAttemptAt = Instant.now();

        // TASK-BE-248 Phase 2b / TASK-BE-259: tenantId from the reused token's DB record.
        // Resolved before publishing so it can flow into auth.token.reuse.detected as well.
        String tenantId = existingToken.getTenantId() != null && !existingToken.getTenantId().isBlank()
                ? existingToken.getTenantId()
                : TenantContext.DEFAULT_TENANT_ID;

        authEventPublisher.publishTokenReuseDetected(
                accountId,
                tenantId,
                jti,
                originalRotationAt,
                reuseAttemptAt,
                ctx.ipMasked(),
                ctx.deviceFingerprint(),
                true,
                revokedCount
        );

        for (DeviceSession session : activeSessions) {
            if (session.isRevoked()) {
                continue;
            }
            List<String> deviceJtis = jtisByDevice.getOrDefault(session.getDeviceId(), List.of());
            session.revoke(reuseAttemptAt, RevokeReason.TOKEN_REUSE);
            deviceSessionRepository.save(session);
            authEventPublisher.publishAuthSessionRevoked(
                    accountId,
                    tenantId,
                    session.getDeviceId(),
                    RevokeReason.TOKEN_REUSE.name(),
                    deviceJtis,
                    reuseAttemptAt,
                    ACTOR_TYPE_SYSTEM,
                    null
            );
        }
    }
}
