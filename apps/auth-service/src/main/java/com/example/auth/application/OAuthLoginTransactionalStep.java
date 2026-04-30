package com.example.auth.application;

import com.example.auth.application.command.OAuthCallbackTxnCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.application.result.OAuthLoginResult;
import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenPair;
import com.example.auth.infrastructure.oauth.OAuthUserInfo;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaEntity;
import com.example.auth.infrastructure.persistence.SocialIdentityJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Transactional boundary for the OAuth callback flow.
 *
 * <p>All DB writes (social identity upsert, refresh token persist, outbox event writes)
 * happen inside a single {@code @Transactional} method on this bean.
 *
 * <p>TASK-BE-229: tokens are issued with default tenant context (fan-platform / B2C_CONSUMER)
 * since OAuth callback does not carry explicit tenant context. The tenant for OAuth flows
 * defaults to "fan-platform" until tenant-scoped OAuth is introduced in a future task.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginTransactionalStep {

    private final TokenGeneratorPort tokenGeneratorPort;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthEventPublisher authEventPublisher;
    private final RegisterOrUpdateDeviceSessionUseCase registerOrUpdateDeviceSessionUseCase;
    private final SocialIdentityJpaRepository socialIdentityJpaRepository;

    @Transactional
    public OAuthLoginResult persistLogin(OAuthCallbackTxnCommand command) {
        OAuthProvider provider = command.provider();
        OAuthUserInfo userInfo = command.userInfo();
        SessionContext ctx = command.sessionContext();
        String accountId = command.accountId();
        boolean isNewAccount = command.isNewAccount();

        // OAuth default tenant: fan-platform / B2C_CONSUMER.
        // When tenant-scoped OAuth is supported (future task), this will be resolved
        // from the OAuth state or account-service response.
        TenantContext tenantContext = TenantContext.defaultContext();

        // Upsert local social identity.
        Optional<SocialIdentityJpaEntity> existingIdentity =
                socialIdentityJpaRepository.findByProviderAndProviderUserId(
                        provider.name(), userInfo.providerUserId());

        if (existingIdentity.isPresent()) {
            var identity = existingIdentity.get();
            identity.updateLastUsedAt();
            if (userInfo.email() != null && !userInfo.email().equals(identity.getProviderEmail())) {
                identity.updateProviderEmail(userInfo.email());
            }
            socialIdentityJpaRepository.save(identity);
        } else {
            var newIdentity = SocialIdentityJpaEntity.create(
                    accountId, tenantContext.tenantId(),
                    provider.name(), userInfo.providerUserId(), userInfo.email());
            socialIdentityJpaRepository.save(newIdentity);
        }

        // Account status check against pre-fetched value (no HTTP here).
        command.accountStatus().ifPresent(this::checkAccountStatus);

        // Register/update device session
        RegisterDeviceSessionResult sessionResult =
                registerOrUpdateDeviceSessionUseCase.execute(accountId, ctx);
        String deviceId = sessionResult.deviceId();

        // Issue JWT tokens with tenant context (fail-closed via JwtTokenGenerator)
        TokenPair tokenPair = tokenGeneratorPort.generateTokenPair(accountId, "user", deviceId,
                tenantContext);

        // Persist refresh token with tenant_id
        String refreshJti = tokenGeneratorPort.extractJti(tokenPair.refreshToken());
        Instant now = Instant.now();
        RefreshToken refreshTokenEntity = RefreshToken.create(
                refreshJti, accountId, tenantContext.tenantId(),
                now,
                now.plusSeconds(tokenGeneratorPort.refreshTokenTtlSeconds()),
                null,
                ctx.deviceFingerprint(),
                deviceId
        );
        refreshTokenRepository.save(refreshTokenEntity);

        // Publish login succeeded event with loginMethod
        authEventPublisher.publishLoginSucceeded(accountId, refreshJti, ctx,
                deviceId, sessionResult.newSession(), provider.loginMethod());

        // Publish session created event if new session
        if (sessionResult.newSession()) {
            authEventPublisher.publishAuthSessionCreated(
                    accountId, deviceId, refreshJti,
                    LoginUseCase.fingerprintHash(ctx.deviceFingerprint()),
                    ctx.userAgentFamily(),
                    ctx.ipMasked(),
                    ctx.resolvedGeoCountry(),
                    now,
                    sessionResult.evictedDeviceIds());
        }

        return new OAuthLoginResult(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.expiresIn(),
                tokenGeneratorPort.refreshTokenTtlSeconds(),
                isNewAccount
        );
    }

    private void checkAccountStatus(String status) {
        switch (status) {
            case "ACTIVE" -> { /* proceed */ }
            case "LOCKED" -> throw new AccountLockedException();
            case "DORMANT" -> throw new AccountStatusException("DORMANT", "ACCOUNT_DORMANT");
            case "DELETED" -> throw new AccountStatusException("DELETED", "ACCOUNT_DELETED");
            default -> throw new AccountStatusException(status, "ACCOUNT_STATUS_UNKNOWN");
        }
    }
}
