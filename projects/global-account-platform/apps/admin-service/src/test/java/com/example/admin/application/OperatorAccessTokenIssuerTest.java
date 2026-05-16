package com.example.admin.application;

import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.security.AdminJwtKeyStore;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.security.jwt.Rs256JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-298 — proves {@link OperatorAccessTokenIssuer} mints the canonical
 * operator access token (the SAME claim set the password+TOTP login path uses
 * — rbac.md D4): {@code sub}, {@code iss=admin-service},
 * {@code token_type=admin}, {@code jti}, {@code iat}/{@code exp}; RS256-signed
 * with the admin IdP key; TTL == the configured operator access TTL.
 */
@DisplayName("OperatorAccessTokenIssuer (shared operator-token minting)")
class OperatorAccessTokenIssuerTest {

    private OperatorAccessTokenIssuer issuer;
    private Rs256JwtVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";

        AdminJwtKeyStore keyStore = new AdminJwtKeyStore(Map.of("test-kid", pem), "test-kid");
        JwtSigner signer = new JwtSigner(keyStore, "admin-service");

        AdminJwtProperties props = new AdminJwtProperties();
        props.setActiveSigningKid("test-kid");
        props.setSigningKeys(Map.of("test-kid", pem));
        props.setIssuer("admin-service");
        props.setExpectedTokenType("admin");
        props.setAccessTokenTtlSeconds(3600L);

        issuer = new OperatorAccessTokenIssuer(signer, props);
        verifier = new Rs256JwtVerifier((RSAPublicKey) kp.getPublic(), "admin-service");
    }

    @Test
    @DisplayName("mints the canonical operator token (sub/iss/token_type=admin/jti) verifiable by the admin IdP key")
    void mintsCanonicalOperatorToken() {
        String token = issuer.mint("00000000-0000-7000-8000-000000000010");

        Map<String, Object> claims = verifier.verify(token);
        assertThat(claims.get("sub")).isEqualTo("00000000-0000-7000-8000-000000000010");
        assertThat(claims.get("iss")).isEqualTo("admin-service");
        assertThat(claims.get("token_type")).isEqualTo("admin");
        assertThat(claims.get("jti")).asString().isNotBlank();
        assertThat(claims.get("iat")).isNotNull();
        assertThat(claims.get("exp")).isNotNull();
    }

    @Test
    @DisplayName("TTL equals the configured operator access TTL")
    void ttlIsOperatorAccessTtl() {
        assertThat(issuer.accessTokenTtlSeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("each mint gets a fresh jti (no replay of the same token id)")
    void freshJtiPerMint() {
        Map<String, Object> a = verifier.verify(issuer.mint("op-1"));
        Map<String, Object> b = verifier.verify(issuer.mint("op-1"));
        assertThat(a.get("jti")).isNotEqualTo(b.get("jti"));
    }
}
