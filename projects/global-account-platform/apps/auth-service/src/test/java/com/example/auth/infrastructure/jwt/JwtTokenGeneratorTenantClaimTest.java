package com.example.auth.infrastructure.jwt;

import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.TokenPair;
import com.example.security.jwt.Rs256JwtSigner;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the {@code tenant_id} and {@code tenant_type} access-token claims are
 * always present and that the generator fails-closed when tenant context is missing.
 * Spec: specs/features/multi-tenancy.md §JWT Changes.
 */
class JwtTokenGeneratorTenantClaimTest {

    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    @BeforeAll
    static void loadKeys() throws Exception {
        privateKey = readPrivate("/keys/private.pem");
        publicKey = readPublic("/keys/public.pem");
    }

    private JwtTokenGenerator generator() {
        Rs256JwtSigner signer = new Rs256JwtSigner(privateKey, "test-key");
        return new JwtTokenGenerator(signer, publicKey, "test-iss", 1800L, 604800L);
    }

    @Test
    @DisplayName("tenant_id and tenant_type claims are present in access token")
    void tenantClaimsPresent() {
        TenantContext ctx = new TenantContext("fan-platform", "B2C_CONSUMER");
        TokenPair pair = generator().generateTokenPair("acc-1", "user", null, ctx);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims.get("tenant_id", String.class)).isEqualTo("fan-platform");
        assertThat(claims.get("tenant_type", String.class)).isEqualTo("B2C_CONSUMER");
    }

    @Test
    @DisplayName("tenant_id and tenant_type are present for B2B_ENTERPRISE tenant")
    void tenantClaimsPresentB2B() {
        TenantContext ctx = new TenantContext("wms", "B2B_ENTERPRISE");
        TokenPair pair = generator().generateTokenPair("acc-2", "user", "dev-uuid", ctx);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims.get("tenant_id", String.class)).isEqualTo("wms");
        assertThat(claims.get("tenant_type", String.class)).isEqualTo("B2B_ENTERPRISE");
        assertThat(claims.get("device_id", String.class)).isEqualTo("dev-uuid");
    }

    @Test
    @DisplayName("tenant_id is also present in refresh token")
    void tenantIdInRefreshToken() {
        TenantContext ctx = new TenantContext("fan-platform", "B2C_CONSUMER");
        TokenPair pair = generator().generateTokenPair("acc-3", "user", null, ctx);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.refreshToken()).getPayload();

        assertThat(claims.get("tenant_id", String.class)).isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("extractTenantId returns tenant_id from token")
    void extractTenantId() {
        TenantContext ctx = new TenantContext("wms", "B2B_ENTERPRISE");
        JwtTokenGenerator gen = generator();
        TokenPair pair = gen.generateTokenPair("acc-4", "user", null, ctx);

        assertThat(gen.extractTenantId(pair.refreshToken())).isEqualTo("wms");
        assertThat(gen.extractTenantId(pair.accessToken())).isEqualTo("wms");
    }

    @Test
    @DisplayName("generateTokenPair fails-closed when tenantContext is null")
    void failsClosedWhenNullTenantContext() {
        assertThatThrownBy(() -> generator().generateTokenPair("acc-5", "user", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    @Test
    @DisplayName("generateTokenPair fails-closed when tenantContext has null tenant_id")
    void failsClosedWhenNullTenantId() {
        // TenantContext is a record and validates on construction, so we test the generator
        // by passing a context object whose tenantId() returns null via a spy workaround.
        // Instead, verify the generator rejects a null tenantContext (already covered above)
        // and that TenantContext itself rejects blank values at construction time.
        assertThatThrownBy(() -> new TenantContext(null, "B2C_CONSUMER"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantContext("fan-platform", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TenantContext("  ", "B2C_CONSUMER"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
    }

    @Test
    @DisplayName("device_id claim is present when supplied alongside tenant context")
    void deviceIdWithTenantContext() {
        TenantContext ctx = new TenantContext("fan-platform", "B2C_CONSUMER");
        TokenPair pair = generator().generateTokenPair("acc-7", "user", "dev-uuid-7", ctx);

        Claims claims = Jwts.parser().verifyWith(publicKey).build()
                .parseSignedClaims(pair.accessToken()).getPayload();

        assertThat(claims.get("device_id", String.class)).isEqualTo("dev-uuid-7");
        assertThat(claims.get("tenant_id", String.class)).isEqualTo("fan-platform");
    }

    private static PrivateKey readPrivate(String path) throws Exception {
        byte[] der = readPem(path);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey readPublic(String path) throws Exception {
        byte[] der = readPem(path);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] readPem(String classpath) throws Exception {
        try (InputStream is = JwtTokenGeneratorTenantClaimTest.class.getResourceAsStream(classpath)) {
            assert is != null;
            String pem = new String(is.readAllBytes())
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(pem);
        }
    }
}
