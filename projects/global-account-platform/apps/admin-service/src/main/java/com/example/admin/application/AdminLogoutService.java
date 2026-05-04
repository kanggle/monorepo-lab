package com.example.admin.application;

import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.JwtVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * TASK-BE-040 — implements {@code POST /api/admin/auth/logout}.
 *
 * <p>The access JWT's jti is written to the Redis blacklist
 * ({@code admin:jti:blacklist:{jti}}, TTL = remaining access-token lifetime)
 * so {@link com.example.admin.infrastructure.security.OperatorAuthenticationFilter}
 * can reject any subsequent use of the same access token. If the caller
 * supplies a refresh token, its registry row is revoked with reason=LOGOUT
 * (best-effort — failure does not undo the blacklist write).
 *
 * <p>TASK-BE-040-fix — uses {@link AdminRefreshTokenPort} /
 * {@link OperatorLookupPort} instead of JPA repositories, removing the
 * application→infrastructure import previously present.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogoutService {

    private final TokenBlacklistPort blacklist;
    private final AdminJwtProperties jwtProperties;
    private final JwtVerifier jwtVerifier;
    private final AdminRefreshTokenPort tokenPort;
    private final OperatorLookupPort operatorLookup;

    @Transactional
    public void logout(String operatorUuid, String accessJti, Instant accessExp, String refreshTokenJwt) {
        if (accessJti == null) {
            // Defensive: filter must populate jti for /logout.
            log.warn("Logout invoked without access jti for operatorId={}", operatorUuid);
            return;
        }
        long ttlSeconds = computeTtlSeconds(accessExp);
        try {
            blacklist.blacklist(accessJti, Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            // Surface as 500 — operator's intent to logout failed and they
            // should retry; the access token remains valid until its natural
            // expiry. Do not silently succeed.
            log.error("Failed to write logout blacklist key for jti={}", accessJti, ex);
            throw ex;
        }

        if (refreshTokenJwt != null && !refreshTokenJwt.isBlank()) {
            revokeRefreshToken(operatorUuid, refreshTokenJwt);
        }
    }

    /**
     * Computes the TTL (seconds) for the Redis blacklist entry.
     *
     * <ul>
     *   <li>When {@code accessExp} is {@code null} (the upstream
     *       {@code OperatorAuthenticationFilter} did not populate the request
     *       attribute — e.g. in tests or on an exception path) we fall back to
     *       the configured access-token TTL. This yields a slightly conservative
     *       over-long blacklist entry but preserves fail-closed semantics for
     *       audit-heavy A10.</li>
     *   <li>When {@code accessExp} is in the past we still write a 1-second
     *       entry so concurrent requests using the same jti observe the
     *       revocation before natural expiry propagates through any caches.</li>
     * </ul>
     */
    private long computeTtlSeconds(Instant accessExp) {
        if (accessExp == null) {
            return jwtProperties.getAccessTokenTtlSeconds();
        }
        long secs = Duration.between(Instant.now(), accessExp).getSeconds();
        if (secs <= 0) return 1L; // already expired; still record briefly so concurrent checks see it
        return secs;
    }

    private void revokeRefreshToken(String operatorUuid, String refreshTokenJwt) {
        Map<String, Object> claims;
        try {
            claims = jwtVerifier.verify(refreshTokenJwt);
        } catch (JwtVerificationException ex) {
            log.debug("logout: refresh token failed verification — ignoring (operatorId={})", operatorUuid);
            return;
        }
        Object tokenType = claims.get("token_type");
        if (!jwtProperties.getRefreshTokenType().equals(tokenType)) return;
        Object jtiObj = claims.get("jti");
        if (jtiObj == null) return;
        String jti = jtiObj.toString();

        tokenPort.findByJti(jti).ifPresent(row -> {
            // Sanity: ensure the refresh token belongs to the operator that
            // owns the access token. Mismatch is silently ignored to avoid
            // leaking presence of someone else's jti.
            operatorLookup.findByOperatorId(operatorUuid).ifPresent(op -> {
                if (op.internalId().equals(row.operatorInternalId()) && !row.isRevoked()) {
                    tokenPort.revoke(jti, Instant.now(), AdminRefreshTokenPort.REASON_LOGOUT);
                }
            });
        });
    }
}
