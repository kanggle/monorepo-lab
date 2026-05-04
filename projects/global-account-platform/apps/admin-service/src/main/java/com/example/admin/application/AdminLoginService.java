package com.example.admin.application;

import com.example.admin.application.exception.EnrollmentRequiredException;
import com.example.admin.application.exception.InvalidCredentialsException;
import com.example.admin.application.exception.InvalidLoginRequestException;
import com.example.admin.application.exception.InvalidRecoveryCodeException;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaEntity;
import com.example.admin.infrastructure.persistence.AdminOperatorTotpJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.admin.infrastructure.security.TotpGenerator;
import com.example.admin.infrastructure.security.TotpSecretCipher;
import com.example.common.id.UuidV7;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.security.password.PasswordHasher;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-029-3 — operator self-login. Password verification via Argon2id,
 * role-driven 2FA enforcement (TOTP or recovery code), operator JWT minting.
 *
 * <p>Timing: on operator lookup miss we perform a dummy
 * {@link PasswordHasher#verify} against a fixed Argon2id hash so the total
 * response time is close to the happy path. The dummy verify intentionally
 * ignores the plaintext/hash result — it exists only to consume CPU time.
 *
 * <p>Recovery code consumption: hashes are stored as a JSON array column on
 * {@code admin_operator_totp}. On match, the hash is removed and the row
 * re-saved. The {@code @Version} column on the entity gives optimistic
 * locking; on conflict we retry exactly once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginService {

    /**
     * Fixed Argon2id hash used by the miss-path dummy verify. Verifies against
     * the string "<timing_dummy>" so the branch is never taken in practice —
     * we only rely on {@link PasswordHasher#verify} running for its constant-time
     * cost. Generated once at boot via the injected hasher.
     */
    private static final String DUMMY_PLAINTEXT = "<timing_dummy_plaintext>";

    private final AdminOperatorJpaRepository operatorRepository;
    private final AdminOperatorRoleJpaRepository operatorRoleRepository;
    private final AdminRoleJpaRepository roleRepository;
    private final AdminOperatorTotpJpaRepository totpRepository;
    private final PasswordHasher passwordHasher;
    private final TotpGenerator totpGenerator;
    private final TotpSecretCipher cipher;
    private final BootstrapTokenService bootstrapTokenService;
    private final JwtSigner jwtSigner;
    private final ObjectMapper objectMapper;
    private final AdminJwtProperties jwtProperties;
    private final AdminRefreshTokenIssuer refreshTokenIssuer;

    private volatile String cachedDummyHash;

    /**
     * Executes the login flow. Returns a {@link LoginResult} describing the
     * outcome; the controller converts this into HTTP + audit.
     */
    @Transactional
    public LoginResult login(String operatorUuid, String password,
                             String totpCode, String recoveryCode) {
        AdminOperatorJpaEntity operator = operatorRepository.findByOperatorId(operatorUuid).orElse(null);

        // Password verification (timing-leveled between miss and wrong-password).
        if (operator == null || operator.getPasswordHash() == null) {
            // Consume comparable CPU time so the caller cannot distinguish miss from wrong password.
            passwordHasher.verify(DUMMY_PLAINTEXT, dummyHash());
            throw new InvalidCredentialsException();
        }
        if (!passwordHasher.verify(password, operator.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        boolean require2fa = roleSetRequires2fa(operator.getId());
        boolean twofaUsed = false;

        if (require2fa) {
            AdminOperatorTotpJpaEntity totpRow = totpRepository.findById(operator.getId()).orElse(null);
            if (totpRow == null) {
                BootstrapTokenService.Issued issued = bootstrapTokenService.issue(
                        operatorUuid,
                        java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY));
                long ttl = java.time.Duration.between(Instant.now(), issued.expiresAt()).getSeconds();
                if (ttl < 0) ttl = 0;
                throw new EnrollmentRequiredException(issued.token(), ttl);
            }
            if (totpRow.getLastUsedAt() == null) {
                // Enrolled but never verified — allow re-enrollment or verify
                BootstrapTokenService.Issued issued = bootstrapTokenService.issue(
                        operatorUuid,
                        java.util.Set.of(BootstrapTokenService.SCOPE_ENROLL, BootstrapTokenService.SCOPE_VERIFY));
                long ttl = java.time.Duration.between(Instant.now(), issued.expiresAt()).getSeconds();
                if (ttl < 0) ttl = 0;
                throw new EnrollmentRequiredException(issued.token(), ttl);
            }
            boolean hasTotp = totpCode != null && !totpCode.isBlank();
            boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();
            if (hasTotp == hasRecovery) {
                throw new InvalidLoginRequestException(
                        "Exactly one of totpCode or recoveryCode must be provided");
            }
            if (hasTotp) {
                byte[] secret = cipher.decrypt(totpRow.getSecretEncrypted(),
                        operator.getId(), totpRow.getSecretKeyId());
                try {
                    if (!totpGenerator.verify(secret, totpCode)) {
                        throw new InvalidTwoFaCodeException("TOTP code does not match");
                    }
                    totpRow.markUsed(Instant.now());
                    totpRepository.save(totpRow);
                } finally {
                    java.util.Arrays.fill(secret, (byte) 0);
                }
            } else {
                consumeRecoveryCode(operator.getId(), recoveryCode);
            }
            twofaUsed = true;
        }

        String accessToken = mintAccessToken(operatorUuid);
        AdminRefreshTokenIssuer.Issued refresh = refreshTokenIssuer.issue(operator.getId(), operatorUuid, null);
        return new LoginResult(
                accessToken,
                jwtProperties.getAccessTokenTtlSeconds(),
                refresh.token(),
                refresh.ttlSeconds(),
                twofaUsed);
    }

    private boolean roleSetRequires2fa(long operatorPk) {
        List<AdminOperatorRoleJpaEntity> bindings = operatorRoleRepository.findByOperatorId(operatorPk);
        if (bindings.isEmpty()) return false;
        List<Long> roleIds = new ArrayList<>(bindings.size());
        for (AdminOperatorRoleJpaEntity b : bindings) roleIds.add(b.getRoleId());
        return roleRepository.findAllById(roleIds).stream().anyMatch(r -> r.isRequire2fa());
    }

    /**
     * Recovery code consumption with a single optimistic-lock retry. On match,
     * removes the hash from the JSON array and saves the row. On conflict
     * (another concurrent login consumed a code), re-reads and retries once.
     */
    private void consumeRecoveryCode(long operatorPk, String rawCode) {
        String normalized = rawCode.trim().toUpperCase();
        for (int attempt = 0; attempt < 2; attempt++) {
            AdminOperatorTotpJpaEntity row = totpRepository.findById(operatorPk)
                    .orElseThrow(() -> new InvalidRecoveryCodeException("2FA not enrolled"));
            List<String> hashes = parseHashes(row.getRecoveryCodesHashed());
            int matchIdx = -1;
            for (int i = 0; i < hashes.size(); i++) {
                if (passwordHasher.verify(normalized, hashes.get(i))) {
                    matchIdx = i;
                    break;
                }
            }
            if (matchIdx < 0) {
                throw new InvalidRecoveryCodeException("Recovery code does not match");
            }
            hashes.remove(matchIdx);
            row.replaceRecoveryHashes(serializeHashes(hashes), Instant.now());
            try {
                totpRepository.saveAndFlush(row);
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
                log.debug("Optimistic lock conflict on recovery code consume (attempt={})", attempt, ex);
                // fall through to retry
            }
        }
        throw new InvalidRecoveryCodeException("Recovery code consume failed after retry");
    }

    private List<String> parseHashes(String json) {
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Malformed recovery_codes_hashed JSON", e);
        }
    }

    private String serializeHashes(List<String> hashes) {
        try {
            return objectMapper.writeValueAsString(hashes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize recovery_codes_hashed", e);
        }
    }

    private String mintAccessToken(String operatorUuid) {
        Instant now = Instant.now();
        long ttl = jwtProperties.getAccessTokenTtlSeconds();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", operatorUuid);
        claims.put("iss", jwtProperties.getIssuer());
        claims.put("jti", UuidV7.randomString());
        claims.put("token_type", jwtProperties.getExpectedTokenType());
        claims.put("iat", now);
        claims.put("exp", now.plusSeconds(ttl));
        return jwtSigner.sign(claims);
    }

    private String dummyHash() {
        String h = cachedDummyHash;
        if (h == null) {
            synchronized (this) {
                h = cachedDummyHash;
                if (h == null) {
                    h = passwordHasher.hash(DUMMY_PLAINTEXT);
                    cachedDummyHash = h;
                }
            }
        }
        return h;
    }

    /**
     * Exposed for callers that want to stamp a consistent idempotency_key on
     * the audit row. Generated per login attempt.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String newIdempotencyKey() {
        return "login:" + UuidV7.randomString();
    }

    /** Return shape of a successful login. */
    public record LoginResult(
            String accessToken,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn,
            boolean twofaUsed
    ) {}
}
