package com.example.admin.application;

import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.common.id.UuidV7;
import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.JwtVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK-BE-040 — implements {@code POST /api/admin/auth/refresh}.
 *
 * <p>Steps (failure stops at the first violation):
 * <ol>
 *   <li>Verify JWT signature/expiry/issuer + {@code token_type=admin_refresh}.</li>
 *   <li>Look up the {@code admin_operator_refresh_tokens} row by jti — missing
 *       row is treated as invalid.</li>
 *   <li>If the row is already revoked → reuse detected: bulk-revoke all the
 *       operator's remaining refresh tokens with reason=REUSE_DETECTED and
 *       throw {@link RefreshTokenReuseDetectedException} carrying the
 *       verified operator UUID (for audit row enrichment).</li>
 *   <li>Otherwise revoke the presented row (reason=ROTATED) and issue a
 *       fresh access+refresh pair via {@link AdminRefreshTokenIssuer} (rotated_from set).</li>
 * </ol>
 *
 * <p>The reuse-detection bulk revoke and the rotation insert run inside the
 * same transaction so partial failure cannot leave the operator with both
 * the old chain and a freshly issued token.
 *
 * <p>TASK-BE-040-fix — JPA repositories no longer imported here. Persistence
 * goes through {@link AdminRefreshTokenPort} / {@link OperatorLookupPort}.
 * The result and reuse-detection exception carry the operator UUID so the
 * controller audit path never decodes unverified JWT payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRefreshTokenService {

    private final JwtVerifier jwtVerifier;
    private final AdminJwtProperties jwtProperties;
    private final AdminRefreshTokenPort tokenPort;
    private final OperatorLookupPort operatorLookup;
    private final AdminRefreshTokenIssuer refreshIssuer;
    private final JwtSigner jwtSigner;

    @Transactional(noRollbackFor = RefreshTokenReuseDetectedException.class)
    public RefreshResult refresh(String refreshTokenJwt) {
        if (refreshTokenJwt == null || refreshTokenJwt.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is missing");
        }

        Map<String, Object> claims;
        try {
            claims = jwtVerifier.verify(refreshTokenJwt);
        } catch (JwtVerificationException ex) {
            throw new InvalidRefreshTokenException("Refresh token signature/exp/iss invalid");
        }

        Object tokenType = claims.get("token_type");
        if (!jwtProperties.getRefreshTokenType().equals(tokenType)) {
            throw new InvalidRefreshTokenException("token_type is not admin_refresh");
        }

        Object subObj = claims.get("sub");
        Object jtiObj = claims.get("jti");
        if (subObj == null || jtiObj == null) {
            throw new InvalidRefreshTokenException("Refresh token missing sub/jti");
        }
        String operatorUuid = subObj.toString();
        String jti = jtiObj.toString();

        AdminRefreshTokenPort.TokenRecord row = tokenPort.findByJti(jti)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token jti not registered"));

        OperatorLookupPort.OperatorSummary operator = operatorLookup.findByOperatorId(operatorUuid)
                .orElseThrow(() -> new InvalidRefreshTokenException("Operator not found for sub"));

        if (!operator.internalId().equals(row.operatorInternalId())) {
            throw new InvalidRefreshTokenException("Refresh token operator mismatch");
        }

        Instant now = Instant.now();
        if (row.isRevoked()) {
            // Reuse of a rotated/revoked jti — treat as compromise of the chain.
            int revoked = tokenPort.revokeAllForOperator(
                    row.operatorInternalId(), now,
                    AdminRefreshTokenPort.REASON_REUSE_DETECTED);
            log.warn("Refresh-token reuse detected: operatorId={} jti={} revokedCount={}",
                    operatorUuid, jti, revoked);
            // The operator UUID passed here was JUST verified via signature +
            // registry row lookup — the controller can safely use it for audit
            // enrichment without re-decoding the raw JWT.
            throw new RefreshTokenReuseDetectedException(
                    "Refresh token has already been revoked; chain invalidated",
                    operator.operatorId());
        }

        // Normal rotation.
        tokenPort.revoke(jti, now, AdminRefreshTokenPort.REASON_ROTATED);

        AdminRefreshTokenIssuer.Issued newRefresh = refreshIssuer.issue(
                row.operatorInternalId(), operatorUuid, jti);
        String accessToken = mintAccessToken(operatorUuid);
        return new RefreshResult(
                accessToken,
                jwtProperties.getAccessTokenTtlSeconds(),
                newRefresh.token(),
                newRefresh.ttlSeconds(),
                operator.operatorId());
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

    /**
     * @param operatorId the verified external UUID of the operator whose chain
     *                   was rotated. Always non-null on success — read from the
     *                   registry row after signature verification, NOT from the
     *                   raw JWT payload.
     */
    public record RefreshResult(
            String accessToken,
            long expiresIn,
            String refreshToken,
            long refreshExpiresIn,
            String operatorId
    ) {}
}
