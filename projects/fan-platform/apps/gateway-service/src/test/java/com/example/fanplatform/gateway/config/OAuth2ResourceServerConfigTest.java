package com.example.fanplatform.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.fanplatform.gateway.security.AllowedIssuersValidator;
import com.example.fanplatform.gateway.security.TenantClaimValidator;

/**
 * Unit-level wiring test for {@link OAuth2ResourceServerConfig#jwtTokenValidator()}.
 * Verifies that the configured validator chain exercises both the issuer allowlist
 * and the tenant claim — the two fan-platform-specific gates — without booting
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
                .claim("tenant_id", "fan-platform")
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
                .issuer("http://gap.local")
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
    void acceptsValidFanPlatformToken() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://gap.local")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "fan-platform")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsLegacyIssuerToken() throws Exception {
        OAuth2ResourceServerConfig config = configWithDefaults();
        OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("global-account-platform")
                .subject("user-1")
                .issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60))
                .claim("tenant_id", "fan-platform")
                .build();

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isFalse();
    }

    private static OAuth2ResourceServerConfig configWithDefaults() throws Exception {
        OAuth2ResourceServerConfig config = new OAuth2ResourceServerConfig();
        writeField(config, "jwkSetUri", "http://gap.local/.well-known/jwks.json");
        writeField(config, "allowedIssuersCsv", "http://gap.local,global-account-platform");
        writeField(config, "requiredTenantId", "fan-platform");
        return config;
    }

    private static void writeField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
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
