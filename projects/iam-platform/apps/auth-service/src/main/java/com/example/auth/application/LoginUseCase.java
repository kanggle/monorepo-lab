package com.example.auth.application;

import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.LoginRateLimitedException;
import com.example.auth.application.exception.LoginTenantAmbiguousException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
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
    private final TenantTypePort tenantTypePort;

    @Value("${auth.login.max-failure-count:5}")
    private int maxFailureCount;

    @Transactional
    public LoginResult execute(LoginCommand command) {
        String emailHash = hashEmail(command.email());
        SessionContext ctx = command.sessionContext();

        // Tenant key for rate-limit lookup. tenantId absent -> "fan-platform" default.
        String tenantIdForRateLimit = command.tenantId() != null ? command.tenantId()
                : TenantContext.DEFAULT_TENANT_ID;

        int failCount = loginAttemptCounter.getFailureCount(tenantIdForRateLimit, emailHash);
        if (failCount >= maxFailureCount) {
            authEventPublisher.publishLoginAttempted(null, emailHash, tenantIdForRateLimit, ctx);
            authEventPublisher.publishLoginFailed(null, emailHash, tenantIdForRateLimit,
                    "RATE_LIMITED", failCount, ctx);
            throw new LoginRateLimitedException();
        }

        authEventPublisher.publishLoginAttempted(null, emailHash, tenantIdForRateLimit, ctx);

        CredentialResolution resolution =
                resolveCredential(command, emailHash, tenantIdForRateLimit, ctx);
        Credential credential = resolution.credential();
        String resolvedTenantId = resolution.resolvedTenantId();
        String accountId = credential.getAccountId();

        if (credential.getCredentialHash() == null) {
            log.warn("Credential row has null hash for accountId={}; treating as invalid", accountId);
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }

        Optional<AccountStatusLookupResult> statusOpt = accountServicePort.getAccountStatus(accountId);
        if (statusOpt.isEmpty()) {
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }
        checkAccountStatus(statusOpt.get().accountStatus(), accountId, emailHash, resolvedTenantId, ctx);

        if (!passwordHasher.verify(command.password(), credential.getCredentialHash())) {
            recordCredentialFailureAndThrow(accountId, emailHash, resolvedTenantId, ctx);
        }

        TokenPair tokenPair =
                issueTokensAndPublishSuccess(accountId, resolvedTenantId, emailHash, ctx);
        return LoginResult.of(tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.expiresIn());
    }

    /**
     * TASK-BE-229: tenant-aware credential lookup. tenantId specified -> single-tenant
     * findByTenantIdAndEmail; absent -> cross-tenant findAllByEmail with ambiguity detection.
     *
     * <p>Records failure side-effects (counter increment, event publish) and throws on every
     * unsuccessful path. Returns the matched credential paired with the resolved tenantId.
     */
    private CredentialResolution resolveCredential(LoginCommand command, String emailHash,
                                                    String tenantIdForRateLimit, SessionContext ctx) {
        if (command.tenantId() != null && !command.tenantId().isBlank()) {
            Optional<Credential> credentialOpt =
                    credentialRepository.findByTenantIdAndEmail(command.tenantId(), command.email());
            if (credentialOpt.isEmpty()) {
                recordCredentialFailureAndThrow(null, emailHash, tenantIdForRateLimit, ctx);
            }
            return new CredentialResolution(credentialOpt.get(), command.tenantId());
        }
        List<Credential> credentials = credentialRepository.findAllByEmail(command.email());
        if (credentials.isEmpty()) {
            recordCredentialFailureAndThrow(null, emailHash, tenantIdForRateLimit, ctx);
            // unreachable — recordCredentialFailureAndThrow always throws
            throw new CredentialsInvalidException();
        }
        if (credentials.size() > 1) {
            log.info("LOGIN_TENANT_AMBIGUOUS: email matches {} tenants; emailHash={}",
                    credentials.size(), emailHash);
            authEventPublisher.publishLoginFailed(null, emailHash, tenantIdForRateLimit,
                    "LOGIN_TENANT_AMBIGUOUS", 0, ctx);
            throw new LoginTenantAmbiguousException();
        }
        Credential credential = credentials.get(0);
        return new CredentialResolution(credential, credential.getTenantId());
    }

    /**
     * Login success path: register device_session, issue token pair, persist the refresh
     * row with tenant_id, reset the failure counter, and publish auth.login.succeeded
     * (+ auth.session.created when a new device_session was created).
     *
     * <p>Device session is touched BEFORE token issuance so the deviceId is available as a
     * JWT claim and as a refresh_tokens.device_id stamp.
     */
    private TokenPair issueTokensAndPublishSuccess(String accountId, String resolvedTenantId,
                                                    String emailHash, SessionContext ctx) {
        // TASK-BE-407: authoritative tenant_type from account-service (cached),
        // replacing the previous hardcoded fallback that misclassified B2C tenants.
        String tenantType = tenantTypePort.resolve(resolvedTenantId);
        TenantContext tenantContext = new TenantContext(resolvedTenantId, tenantType);

        RegisterDeviceSessionResult sessionResult =
                registerOrUpdateDeviceSessionUseCase.execute(accountId, resolvedTenantId, ctx);
        String deviceId = sessionResult.deviceId();

        TokenPair tokenPair = tokenGeneratorPort.generateTokenPair(accountId, "user", deviceId,
                tenantContext);

        String refreshJti = tokenGeneratorPort.extractJti(tokenPair.refreshToken());
        Instant now = Instant.now();
        RefreshToken refreshTokenEntity = RefreshToken.create(
                refreshJti, accountId, resolvedTenantId,
                now,
                now.plusSeconds(tokenGeneratorPort.refreshTokenTtlSeconds()),
                null,
                ctx.deviceFingerprint(),
                deviceId);
        refreshTokenRepository.save(refreshTokenEntity);

        loginAttemptCounter.resetFailureCount(resolvedTenantId, emailHash);

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

        return tokenPair;
    }

    private record CredentialResolution(Credential credential, String resolvedTenantId) {}

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
