package com.example.admin.application;

import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.security.JwtSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * TASK-BE-040 — issues a refresh JWT and persists its registry row in a
 * single transaction so the JWT is never returned to the client without a
 * matching {@code admin_operator_refresh_tokens} entry.
 *
 * <p>Used by {@code AdminLoginService#login(...)} (no {@code rotated_from}) and
 * {@link AdminRefreshTokenService} (sets {@code rotated_from} = previous jti).
 * Both call sites are already inside a {@code @Transactional} boundary; this
 * component declares {@link Propagation#MANDATORY} so the contract is enforced
 * at runtime.
 *
 * <p>TASK-BE-040-fix — writes through {@link AdminRefreshTokenPort}; no JPA
 * repository is imported here (architecture.md Allowed Dependencies).
 */
@Component
@RequiredArgsConstructor
public class AdminRefreshTokenIssuer {

    private final JwtSigner jwtSigner;
    private final AdminRefreshTokenPort tokenPort;
    private final AdminJwtProperties properties;

    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issue(Long operatorPk, String operatorUuid, String rotatedFromJti) {
        Instant now = Instant.now();
        long ttl = properties.getRefreshTokenTtlSeconds();
        Instant exp = now.plusSeconds(ttl);
        String jti = UUID.randomUUID().toString();

        tokenPort.insert(new AdminRefreshTokenPort.NewTokenRecord(
                jti, operatorPk, now, exp, rotatedFromJti));

        String token = jwtSigner.signRefresh(operatorUuid, jti, properties.getRefreshTokenType(), now, exp);
        return new Issued(token, jti, ttl, exp);
    }

    public record Issued(String token, String jti, long ttlSeconds, Instant expiresAt) {}
}
