package com.example.scmplatform.gateway.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.junit.jupiter.api.Test;

/**
 * No-Docker self-test for {@link JwtTestHelper}. Confirms the helper produces
 * correctly-shaped, cryptographically valid tokens against the matching JWKS.
 */
class JwtTestHelperTest {

    private final JwtTestHelper helper = new JwtTestHelper();

    @Test
    void scmTokenParsesAndVerifiesAgainstItsOwnJwks() throws Exception {
        String token = helper.signScmToken("user-42");

        JWTClaimsSet claims = decodeAndVerify(token);

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.getStringClaim("role")).isEqualTo("BUYER");
        assertThat(claims.getStringListClaim("roles")).containsExactly("BUYER");
        assertThat(claims.getStringClaim("email")).isEqualTo("user-42@test.local");
        assertThat(claims.getIssuer()).isEqualTo(JwtTestHelper.SAS_ISSUER);
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo(JwtTestHelper.DEFAULT_TENANT_ID);
        assertThat(claims.getExpirationTime()).isAfter(new java.util.Date());
    }

    @Test
    void clientCredentialsTokenCarriesScopeAndAzp() throws Exception {
        String token = helper.signClientCredentialsToken();

        JWTClaimsSet claims = decodeAndVerify(token);

        // Edge Case E1: client_credentials sub = client_id.
        assertThat(claims.getSubject()).isEqualTo(JwtTestHelper.INTERNAL_CLIENT_ID);
        assertThat(claims.getStringClaim("azp")).isEqualTo(JwtTestHelper.INTERNAL_CLIENT_ID);
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo("scm");
        assertThat(claims.getStringClaim("scope")).isEqualTo("scm.read scm.write");
        // Edge Case E3: no role / roles / email claim.
        assertThat(claims.getClaim("role")).isNull();
        assertThat(claims.getClaim("email")).isNull();
        assertThat(claims.getAudience()).contains(JwtTestHelper.INTERNAL_CLIENT_ID);
    }

    @Test
    void superAdminTokenCarriesWildcardTenant() throws Exception {
        String token = helper.signSuperAdminToken("admin-1");
        JWTClaimsSet claims = decodeAndVerify(token);
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo("*");
        assertThat(claims.getStringClaim("role")).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void crossTenantTokenCarriesWmsTenant() throws Exception {
        String token = helper.signCrossTenantToken("wms-user");
        JWTClaimsSet claims = decodeAndVerify(token);
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo("wms");
    }

    @Test
    void tokenSignedByOneHelperDoesNotVerifyAgainstAnother() throws Exception {
        JwtTestHelper other = new JwtTestHelper();
        String token = helper.signToken("u", "R", "scm", 300);

        ConfigurableJWTProcessor<SecurityContext> processor = buildProcessor(other.jwksJson());

        assertThatThrownBy(() -> processor.process(token, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void jwksJsonExposesPublicKeyOnly() throws Exception {
        JWKSet set = JWKSet.parse(helper.jwksJson());
        assertThat(set.getKeys()).hasSize(1);
        RSAKey rsa = (RSAKey) set.getKeys().get(0);
        assertThat(rsa.isPrivate()).isFalse();
        assertThat(rsa.getKeyID()).isEqualTo(helper.keyId());
    }

    /**
     * 🔴 TASK-MONO-542 regression guard. The tampering used to be three lines inlined in
     * {@code GatewayBootstrapIntegrationTest}, and it flipped the LAST signature
     * character — which for RS256/2048 carries 2 real bits and 4 padding bits, so
     * {@code 'A'} → {@code 'B'} changed nothing that mattered. Measured over 400 tokens
     * before the fix: the signature bytes survived intact 107 times (26.75%), every one
     * of them a signature ending in {@code 'A'}. Roughly a quarter of runs therefore sent
     * a VALID token to a test asserting 401, and failed five seconds later on a
     * downstream read timeout that looked like flaky infrastructure.
     *
     * <p>Run over many tokens on purpose. A single sample passes ~73% of the time by luck,
     * which is exactly how the original defect stayed invisible — a guard that samples once
     * would have shipped green and proved nothing.
     */
    @Test
    void tamperSignatureAlwaysProducesATokenThatFailsVerification() {
        int samples = 300;
        int stillValid = 0;

        for (int i = 0; i < samples; i++) {
            String tampered = JwtTestHelper.tamperSignature(helper.signScmToken("probe-" + i));
            try {
                decodeAndVerify(tampered);
                stillValid++;
            } catch (Exception expected) {
                // correct: the signature no longer verifies
            }
        }

        assertThat(stillValid)
                .as("tamperSignature must invalidate every token; %d of %d still verified",
                        stillValid, samples)
                .isZero();
    }

    /**
     * The byte-level half of the same guard. Also multi-sample, and deliberately so: a
     * one-token version of this assertion was written first, and it would have caught the
     * original defect only about a quarter of the time — the same coin-flip that let the
     * defect live in CI. Measured with the old logic restored, this loop reports 73 of 300
     * tokens surviving intact; a single sample reports "pass" three times out of four.
     */
    @Test
    void tamperSignatureChangesTheDecodedSignatureBytes() {
        int samples = 300;
        int unchanged = 0;

        for (int i = 0; i < samples; i++) {
            String token = helper.signScmToken("probe-" + i);
            String original = token.split("\\.")[2];
            String tampered = JwtTestHelper.tamperSignature(token).split("\\.")[2];

            assertThat(tampered).hasSameSizeAs(original);
            if (java.util.Arrays.equals(new Base64URL(original).decode(),
                    new Base64URL(tampered).decode())) {
                unchanged++;
            }
        }

        assertThat(unchanged)
                .as("the point of tampering is that the BYTES differ, not that the text does; "
                        + "%d of %d tokens decoded to identical signature bytes", unchanged, samples)
                .isZero();
    }

    private JWTClaimsSet decodeAndVerify(String token) throws Exception {
        return buildProcessor(helper.jwksJson()).process(token, null);
    }

    private static ConfigurableJWTProcessor<SecurityContext> buildProcessor(String jwksJson) throws Exception {
        JWKSet set = JWKSet.parse(jwksJson);
        ImmutableJWKSet<SecurityContext> source = new ImmutableJWKSet<>(set);
        JWSKeySelector<SecurityContext> selector =
                new JWSVerificationKeySelector<SecurityContext>(JWSAlgorithm.RS256, source);
        ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(selector);
        return processor;
    }
}
