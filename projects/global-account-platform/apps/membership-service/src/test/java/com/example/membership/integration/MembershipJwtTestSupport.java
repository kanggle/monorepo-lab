package com.example.membership.integration;

import com.example.membership.infrastructure.security.AllowedIssuersValidator;
import com.example.membership.infrastructure.security.TenantClaimValidator;
import com.example.security.jwt.Rs256JwtSigner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared JWT signing + decoding fixtures for membership-service integration tests
 * (TASK-BE-253). Uses an in-memory RSA key pair generated per JVM so the test
 * does not depend on an external auth-service or JWKS endpoint.
 */
public final class MembershipJwtTestSupport {

    public static final String LEGACY_ISSUER = "global-account-platform";
    public static final String SAS_ISSUER = "http://localhost:8081";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private static final KeyPair KEY_PAIR = generateKeyPair();
    private static final Rs256JwtSigner SIGNER =
            new Rs256JwtSigner(KEY_PAIR.getPrivate(), "test-key-001");

    private MembershipJwtTestSupport() {}

    public static String bearer(String accountId, List<String> roles) {
        return "Bearer " + token(accountId, roles, LEGACY_ISSUER, DEFAULT_TENANT_ID);
    }

    public static String bearerSas(String accountId, List<String> roles) {
        return "Bearer " + token(accountId, roles, SAS_ISSUER, DEFAULT_TENANT_ID);
    }

    public static String bearerWithTenant(String accountId, List<String> roles, String tenantId) {
        return "Bearer " + token(accountId, roles, LEGACY_ISSUER, tenantId);
    }

    public static String token(String accountId, List<String> roles, String issuer, String tenantId) {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", accountId);
        claims.put("roles", roles);
        claims.put("iss", issuer);
        claims.put("tenant_id", tenantId);
        claims.put("iat", now);
        claims.put("exp", now.plus(30, ChronoUnit.MINUTES));
        return SIGNER.sign(claims);
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Imports this {@link TestConfiguration} from a {@code @SpringBootTest} integration
     * test to override the production {@link JwtDecoder} that fetches JWKS over the
     * network.
     */
    @TestConfiguration
    public static class JwtDecoderConfig {

        @Bean
        public JwtDecoder jwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey()).build();
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new AllowedIssuersValidator(List.of(SAS_ISSUER, LEGACY_ISSUER)),
                    new TenantClaimValidator(DEFAULT_TENANT_ID));
            decoder.setJwtValidator(validator);
            return decoder;
        }
    }
}
