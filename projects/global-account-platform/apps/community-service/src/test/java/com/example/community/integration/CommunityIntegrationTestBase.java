package com.example.community.integration;

import com.example.community.infrastructure.security.AllowedIssuersValidator;
import com.example.community.infrastructure.security.TenantClaimValidator;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.example.security.jwt.Rs256JwtSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base class for community-service integration tests.
 *
 * <p>TASK-BE-253 changes:
 * <ul>
 *   <li>Tokens now carry {@code tenant_id} (default: {@code fan-platform}).</li>
 *   <li>Provides a {@link JwtDecoder} bean built from the local test public key,
 *       short-circuiting the production decoder that would fetch JWKS from a
 *       real OIDC issuer URL.</li>
 *   <li>{@link #bearerToken(String, List)} continues to issue legacy-shape tokens
 *       ({@code iss=global-account-platform}). Use {@link #bearerSasToken} or
 *       {@link #bearerTokenWithTenant} for the new validation regression scenarios.</li>
 * </ul>
 */
@ActiveProfiles("test")
@Import(CommunityIntegrationTestBase.TestJwtDecoderConfig.class)
public abstract class CommunityIntegrationTestBase extends AbstractIntegrationTest {

    public static final String LEGACY_ISSUER = "global-account-platform";
    public static final String SAS_ISSUER = "http://localhost:8081";
    public static final String DEFAULT_TENANT_ID = "fan-platform";

    protected static final WireMockServer MEMBERSHIP_WM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    protected static final WireMockServer ACCOUNT_WM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static Rs256JwtSigner signer;

    @BeforeAll
    static void startWireMock() throws Exception {
        if (!MEMBERSHIP_WM.isRunning()) {
            MEMBERSHIP_WM.start();
        }
        if (!ACCOUNT_WM.isRunning()) {
            ACCOUNT_WM.start();
        }
        signer = new Rs256JwtSigner(loadTestPrivateKey(), "test-key-001");
    }

    @AfterAll
    static void stopWireMock() {
        if (MEMBERSHIP_WM.isRunning()) {
            MEMBERSHIP_WM.stop();
        }
        if (ACCOUNT_WM.isRunning()) {
            ACCOUNT_WM.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        MEMBERSHIP_WM.resetAll();
        ACCOUNT_WM.resetAll();
    }

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("community.membership-service.base-url", MEMBERSHIP_WM::baseUrl);
        registry.add("community.account-service.base-url", ACCOUNT_WM::baseUrl);
    }

    protected String bearerToken(String accountId, List<String> roles) {
        return "Bearer " + buildToken(accountId, roles, LEGACY_ISSUER, DEFAULT_TENANT_ID);
    }

    protected String bearerSasToken(String accountId, List<String> roles) {
        return "Bearer " + buildToken(accountId, roles, SAS_ISSUER, DEFAULT_TENANT_ID);
    }

    protected String bearerTokenWithTenant(String accountId, List<String> roles, String tenantId) {
        return "Bearer " + buildToken(accountId, roles, LEGACY_ISSUER, tenantId);
    }

    private String buildToken(String accountId, List<String> roles, String issuer, String tenantId) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", accountId);
        claims.put("roles", roles);
        claims.put("iss", issuer);
        claims.put("tenant_id", tenantId);
        claims.put("iat", Instant.now());
        claims.put("exp", Instant.now().plus(30, ChronoUnit.MINUTES));
        return signer.sign(claims);
    }

    private static PrivateKey loadTestPrivateKey() throws Exception {
        try (InputStream is = CommunityIntegrationTestBase.class.getResourceAsStream("/keys/private.pem")) {
            if (is == null) {
                throw new IllegalStateException("classpath:/keys/private.pem not found");
            }
            String pem = new String(is.readAllBytes())
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
    }

    /**
     * Provides a {@link JwtDecoder} bean for integration tests that verifies tokens
     * with the locally bundled public key. This intentionally bypasses the OIDC
     * discovery flow used in production — there is no auth-service running in the
     * Testcontainers stack.
     */
    @TestConfiguration
    static class TestJwtDecoderConfig {

        @Bean
        public JwtDecoder jwtDecoder() throws Exception {
            RSAPublicKey publicKey = loadPublicKey();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(),
                    new AllowedIssuersValidator(List.of(SAS_ISSUER, LEGACY_ISSUER)),
                    new TenantClaimValidator(DEFAULT_TENANT_ID));
            decoder.setJwtValidator(validator);
            return decoder;
        }

        private RSAPublicKey loadPublicKey() throws Exception {
            try (InputStream is = CommunityIntegrationTestBase.class
                    .getResourceAsStream("/keys/public.pem")) {
                if (is == null) {
                    throw new IllegalStateException("classpath:/keys/public.pem not found");
                }
                String pem = new String(is.readAllBytes())
                        .replaceAll("-----BEGIN [A-Z ]+-----", "")
                        .replaceAll("-----END [A-Z ]+-----", "")
                        .replaceAll("\\s", "");
                byte[] keyBytes = Base64.getDecoder().decode(pem);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
            }
        }
    }
}
