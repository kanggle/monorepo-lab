package com.example.scmplatform.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;

/**
 * Unit-level wiring test for {@link OAuth2ResourceServerConfig#jwtTokenValidator()}.
 * Verifies that the configured validator chain exercises both the issuer allowlist
 * and the tenant claim — the two scm-platform-specific gates — without booting
 * a Spring context.
 */
class OAuth2ResourceServerConfigTest {

    @Test
    void chainsIssuerAndTenantValidators() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        assertThat(validator).isInstanceOf(DelegatingOAuth2TokenValidator.class);
        // Spring Security's DelegatingOAuth2TokenValidator stores its delegates
        // in a private final List<OAuth2TokenValidator<?>> tokenValidators field.
        // We reflectively inspect to assert presence of the two custom validators.
        @SuppressWarnings("unchecked")
        List<OAuth2TokenValidator<Jwt>> delegates =
                (List<OAuth2TokenValidator<Jwt>>) readField(validator, "tokenValidators");
        assertThat(delegates).anyMatch(AllowedIssuersValidator.class::isInstance);
        assertThat(delegates).anyMatch(TenantClaimValidator.class::isInstance);
    }

    @Test
    void rejectsUnknownIssuerThroughChain() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://attacker.example")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "scm")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    @Test
    void rejectsCrossTenantThroughChain() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "wms")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    void acceptsValidScmToken() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "scm")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("TASK-MONO-367 — 레거시 발급자(iam)는 일몰 후 거부된다 (BE-398 이후)")
    void rejectsLegacyIssuerToken() throws Exception {
        // Before TASK-MONO-367 this token PASSED — the allowlist carried `iam` for the D2-b
        // deprecation window. TASK-BE-398 retired the only flow that minted `iss=iam`, so the
        // allowlist below (configWithDefaults) no longer carries it either.
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("iam")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "scm")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
    }

    /**
     * The config now takes its properties on the constructor (TASK-MONO-355), so the
     * reflective field-write this helper used to need is gone. Same values, same keys.
     *
     * <p>TASK-MONO-367 (2026-08-01 sunset, LANDED): the allowed-issuers value matches
     * production post-sunset — SAS issuer only, no trailing {@code ,iam}.
     */
    private static OAuth2ResourceServerConfig configWithDefaults() {
        return new OAuth2ResourceServerConfig(
                "http://iam.local/oauth2/jwks", "http://iam.local", "scm");
    }

    private static Object readField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
