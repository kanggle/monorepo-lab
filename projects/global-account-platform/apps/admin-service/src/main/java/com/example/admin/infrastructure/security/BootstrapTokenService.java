package com.example.admin.infrastructure.security;

import com.example.admin.application.exception.InvalidBootstrapTokenException;
import com.example.common.id.UuidV7;
import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.JwtVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Issues and verifies the short-lived bootstrap token used by the 2FA
 * enroll/verify sub-tree (specs/services/admin-service/security.md
 * §Bootstrap Token). Not a full operator session — it only authorises
 * {@code /api/admin/auth/2fa/**}.
 *
 * <ul>
 *   <li>Signing: reuses the operator JWT key set (kid rotation benefit).</li>
 *   <li>TTL: 10 minutes (per security.md §Bootstrap Token).</li>
 *   <li>Claims: {@code sub=operatorId, iss=admin-service, jti=uuidV7,
 *       iat, exp, token_type=admin_bootstrap}.</li>
 *   <li>Replay defense: Redis {@code SETNX admin:bootstrap:jti:{jti}}
 *       with TTL 15 minutes (slightly longer than token TTL). Redis down →
 *       fail-closed (401) so a replayed token cannot slip through.</li>
 * </ul>
 */
@Slf4j
public class BootstrapTokenService {

    public static final String TOKEN_TYPE = "admin_bootstrap";
    public static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    public static final Duration JTI_TTL = Duration.ofMinutes(15);
    private static final String JTI_KEY_PREFIX = "admin:bootstrap:jti:";

    public static final String SCOPE_ENROLL = "2fa_enroll";
    public static final String SCOPE_VERIFY = "2fa_verify";
    /** Default scope set minted on login enrollment-required path. */
    public static final Set<String> DEFAULT_SCOPES = Set.of(SCOPE_ENROLL, SCOPE_VERIFY);

    private final JwtSigner signer;
    private final JwtVerifier verifier;
    private final StringRedisTemplate redis;
    private final String issuer;

    public BootstrapTokenService(JwtSigner signer,
                                 JwtVerifier verifier,
                                 StringRedisTemplate redis,
                                 String issuer) {
        this.signer = Objects.requireNonNull(signer);
        this.verifier = Objects.requireNonNull(verifier);
        this.redis = Objects.requireNonNull(redis);
        this.issuer = Objects.requireNonNull(issuer);
    }

    /**
     * Mints a bootstrap token for {@code operatorId}. Caller is responsible
     * for having already authenticated the operator via password (029-3 login).
     */
    public Issued issue(String operatorId) {
        return issue(operatorId, DEFAULT_SCOPES);
    }

    /**
     * Mints a bootstrap token with an explicit {@code scope} claim (per
     * security.md §Bootstrap Token / admin-api.md §Bootstrap Token).
     */
    public Issued issue(String operatorId, Collection<String> scopes) {
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(scopes, "scopes must not be null");
        if (scopes.isEmpty()) {
            throw new IllegalArgumentException("scopes must not be empty");
        }
        Instant now = Instant.now();
        String jti = UuidV7.randomString();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", operatorId);
        claims.put("iss", issuer);
        claims.put("jti", jti);
        claims.put("token_type", TOKEN_TYPE);
        claims.put("scope", new ArrayList<>(scopes));
        claims.put("iat", now);
        claims.put("exp", now.plus(TOKEN_TTL));
        String token = signer.sign(claims);
        return new Issued(token, jti, now.plus(TOKEN_TTL));
    }

    /**
     * Verifies the token and consumes its {@code jti} exactly once. Subsequent
     * calls with the same token are rejected as replays.
     *
     * @throws InvalidBootstrapTokenException on signature/claim/replay failures
     */
    public BootstrapContext verifyAndConsume(String compact) {
        return verifyAndConsume(compact, null);
    }

    /**
     * Variant that additionally enforces the token carries {@code requiredScope}
     * in its {@code scope} claim. {@code null} disables the scope check (for
     * legacy callers and tests).
     */
    public BootstrapContext verifyAndConsume(String compact, String requiredScope) {
        Map<String, Object> claims;
        try {
            claims = verifier.verify(compact);
        } catch (JwtVerificationException e) {
            throw new InvalidBootstrapTokenException("Bootstrap token invalid", e);
        }
        Object tokenType = claims.get("token_type");
        if (!TOKEN_TYPE.equals(tokenType)) {
            throw new InvalidBootstrapTokenException("token_type is not admin_bootstrap");
        }
        Object sub = claims.get("sub");
        Object jti = claims.get("jti");
        if (sub == null || jti == null) {
            throw new InvalidBootstrapTokenException("Missing sub or jti");
        }
        if (requiredScope != null) {
            Object scopeClaim = claims.get("scope");
            if (!(scopeClaim instanceof Collection<?> c) || !c.contains(requiredScope)) {
                throw new InvalidBootstrapTokenException(
                        "Bootstrap token missing required scope: " + requiredScope);
            }
        }
        String jtiStr = jti.toString();

        // Replay defence: SETNX jti key with 15-min TTL. Redis unavailable =>
        // fail-closed (cannot guarantee single-use).
        Boolean set;
        try {
            set = redis.opsForValue().setIfAbsent(JTI_KEY_PREFIX + jtiStr, "1", JTI_TTL);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable while consuming bootstrap jti (fail-closed)", e);
            throw new InvalidBootstrapTokenException("Bootstrap jti store unavailable", e);
        } catch (RuntimeException e) {
            log.error("Unexpected error consuming bootstrap jti", e);
            throw new InvalidBootstrapTokenException("Bootstrap jti consume failed", e);
        }
        if (set == null || !set) {
            throw new InvalidBootstrapTokenException("Bootstrap token jti already consumed");
        }
        return new BootstrapContext(sub.toString(), jtiStr);
    }

    public record Issued(String token, String jti, Instant expiresAt) {}
}
