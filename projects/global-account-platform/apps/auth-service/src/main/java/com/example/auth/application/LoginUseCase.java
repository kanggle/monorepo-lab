package com.example.auth.application;

import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.LoginRateLimitedException;
import com.example.auth.application.exception.LoginTenantAmbiguousException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.LoginResult;
import com.example.auth.application.result.RegisterDeviceSessionResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.LoginAttemptCounter;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenPair;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase {

    private final CredentialRepository credentialRepository;
    private final AccountServicePort accountServicePort;
    private final PasswordHasher passwordHasher;
    private final TokenGeneratorPort tokenGeneratorPort;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptCounter loginAttemptCounter;
    private final AuthEventPublisher authEventPublisher;
    private final RegisterOrUpdateDeviceSessionUseCase registerOrUpdateDeviceSessionUseCase;

    @Value("${auth.login.max-failure-count:5}")
    private int maxFailureCount;

    @Transactional
    public LoginResult execute(LoginCommand command) {
        String emailHash = hashEmail(command.email());
        SessionContext ctx = command.sessionContext();

        // Determine tenant for rate-limiting key.
        // If tenantId is specified, use it; otherwise fall back to "fan-platform" for key lookup.
        String tenantIdForRateLimit = command.tenantId() != null ? command.tenantId()
                : TenantContext.DEFAULT_TENANT_ID;

        // Check rate limit using tenant-aware key (TASK-BE-229).
        int failCount = loginAttemptCounter.getFailureCount(tenantIdForRateLimit, emailHash);
        if (failCount >= maxFailureCount) {
            authEventPublisher.publishLoginAttempted(null, emailHash, tenantIdForRateLimit, ctx);
            authEventPublisher.publishLoginFailed(null, emailHash, tenantIdForRateLimit,
                    "RATE_LIMITED", failCount, ctx);
            throw new LoginRateLimitedException();
        }

        // Publish login attempted event
        authEventPublisher.publishLoginAttempted(null, emailHash, tenantIdForRateLimit, ctx);

        // TASK-BE-229: tenant-aware credential lookup.
        // If tenantId is specified → single-tenant lookup.
        // If tenantId is absent → cross-tenant lookup for ambiguity detection.
        final Credential credential;
        final String resolvedTenantId;

        if (command.tenantId() != null && !command.tenantId().isBlank()) {
            // Tenant-specific lookup
            Optional<Credential> credentialOpt =
                    credentialRepository.findByTenantIdAndEmail(command.tenantId(), command.email());
            if (credentialOpt.isEmpty()) {
                recordCredentialFailureAndThrow(null, emailHash, tenantIdForRateLimit, ctx);
            }
            credential = credentialOpt.get();
            resolvedTenantId = command.tenantId();
        } else {
            // Cross-tenant lookup for ambiguity detection
            List<Credential> credentials = credentialRepository.findAllByEmail(command.email());
            if (credentials.isEmpty()) {
                recordCredentialFailureAndThrow(null, emailHash, tenantIdForRateLimit, ctx);
                // unreachable — recordCredentialFailureAndThrow always throws
                throw new CredentialsInvalidException();
            }
            if (credentials.size() > 1) {
                // Multiple tenants have the same email — require explicit tenantId
                log.info("LOGIN_TENANT_AMBIGUOUS: email matches {} tenants; emailHash={}",
                        credentials.size(), emailHash);
                authEventPublisher.publishLoginFailed(null, emailHash, tenantIdForRateLimit,
                        "LOGIN_TENANT_AMBIGUOUS", 0, ctx);
                throw new LoginTenantAmbiguousException();
            }
            credential = credentials.get(0);
            resolvedTenantId = credential.getTenantId();
        }

        String accountId = credential.getAccountId();

        // Short-lived null-guard: if a credential row somehow carries a null hash, treat as invalid.
        if (credential.getCredentialHash() == null) {
            log.warn("Credential row has null hash for accountId={}; treating as invalid", accountId);
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }

        // Account status is still owned by account-service.
        Optional<AccountStatusLookupResult> statusOpt = accountServicePort.getAccountStatus(accountId);
        if (statusOpt.isEmpty()) {
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }
        AccountStatusLookupResult status = statusOpt.get();

        checkAccountStatus(status.accountStatus(), accountId, emailHash, resolvedTenantId, ctx);

        // Verify password
        boolean passwordValid = passwordHasher.verify(command.password(), credential.getCredentialHash());
        if (!passwordValid) {
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }

        // Build tenant context for token issuance — tenantType is embedded in the credential.
        // Currently credentials don't carry tenantType so we use the default mapping.
        // TODO: when account-service exposes tenantType in credential lookup, use that value.
        String tenantType = TenantContext.resolveTenantType(resolvedTenantId);
        TenantContext tenantContext = new TenantContext(resolvedTenantId, tenantType);

        // Login success — register/touch the device_session BEFORE issuing tokens so the
        // device_id is available as a JWT claim and as a refresh_tokens.device_id stamp.
        RegisterDeviceSessionResult sessionResult =
                registerOrUpdateDeviceSessionUseCase.execute(accountId, resolvedTenantId, ctx);
        String deviceId = sessionResult.deviceId();

        TokenPair tokenPair = tokenGeneratorPort.generateTokenPair(accountId, "user", deviceId,
                tenantContext);

        // Extract JTI from refresh token and persist with tenant_id
        String refreshJti = tokenGeneratorPort.extractJti(tokenPair.refreshToken());
        Instant now = Instant.now();
        RefreshToken refreshTokenEntity = RefreshToken.create(
                refreshJti, accountId, resolvedTenantId,
                now,
                now.plusSeconds(tokenGeneratorPort.refreshTokenTtlSeconds()),
                null,
                ctx.deviceFingerprint(),
                deviceId
        );
        refreshTokenRepository.save(refreshTokenEntity);

        // Reset failure counter (tenant-aware key)
        loginAttemptCounter.resetFailureCount(resolvedTenantId, emailHash);

        // Publish success events
        authEventPublisher.publishLoginSucceeded(accountId, refreshJti, resolvedTenantId,
                ctx, deviceId, sessionResult.newSession());
        if (sessionResult.newSession()) {
            authEventPublisher.publishAuthSessionCreated(
                    accountId, resolvedTenantId, deviceId, refreshJti,
                    fingerprintHash(ctx.deviceFingerprint()),
                    ctx.userAgentFamily(),
                    ctx.ipMasked(),
                    ctx.resolvedGeoCountry(),
                    now,
                    sessionResult.evictedDeviceIds());
        }

        return LoginResult.of(tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.expiresIn());
    }

    /**
     * SHA-256 of the raw fingerprint, hex-encoded. Used for {@code auth.session.created}
     * payload's {@code deviceFingerprintHash}.
     */
    static String fingerprintHash(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Records a CREDENTIALS_INVALID failure (increment counter, publish event)
     * and throws {@link CredentialsInvalidException}. Never returns normally.
     */
    private void recordCredentialFailureAndThrow(String accountId, String emailHash,
                                                   String tenantId, SessionContext ctx) {
        loginAttemptCounter.incrementFailureCount(tenantId, emailHash);
        int newCount = loginAttemptCounter.getFailureCount(tenantId, emailHash);
        authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                "CREDENTIALS_INVALID", newCount, ctx);
        throw new CredentialsInvalidException();
    }

    private void checkAccountStatus(String status, String accountId, String emailHash,
                                     String tenantId, SessionContext ctx) {
        switch (status) {
            case "ACTIVE" -> { /* proceed */ }
            case "LOCKED" -> {
                authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                        "ACCOUNT_LOCKED", 0, ctx);
                throw new AccountLockedException();
            }
            case "DORMANT" -> {
                authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                        "ACCOUNT_DORMANT", 0, ctx);
                throw new AccountStatusException("DORMANT", "ACCOUNT_DORMANT");
            }
            case "DELETED" -> {
                authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                        "ACCOUNT_DELETED", 0, ctx);
                throw new AccountStatusException("DELETED", "ACCOUNT_DELETED");
            }
            default -> {
                authEventPublisher.publishLoginFailed(accountId, emailHash, tenantId,
                        "ACCOUNT_STATUS_UNKNOWN", 0, ctx);
                throw new AccountStatusException(status, "ACCOUNT_STATUS_UNKNOWN");
            }
        }
    }

    static String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
