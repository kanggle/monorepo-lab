package com.example.fanplatform.gateway.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
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
    void fanTokenParsesAndVerifiesAgainstItsOwnJwks() throws Exception {
        String token = helper.signFanToken("user-42");

        JWTClaimsSet claims = decodeAndVerify(token);

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.getStringClaim("role")).isEqualTo("FAN");
        assertThat(claims.getStringListClaim("roles")).containsExactly("FAN");
        assertThat(claims.getStringClaim("email")).isEqualTo("user-42@test.local");
        assertThat(claims.getIssuer()).isEqualTo(JwtTestHelper.SAS_ISSUER);
        assertThat(claims.getStringClaim("tenant_id")).isEqualTo(JwtTestHelper.DEFAULT_TENANT_ID);
        assertThat(claims.getExpirationTime()).isAfter(new java.util.Date());
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
        String token = helper.signToken("u", "R", "fan-platform", 300);

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
     * 🔴 TASK-MONO-543 regression guard for {@link JwtTestHelper#signForgedSignatureToken}.
     *
     * <p>The integration suite used to tamper inline by flipping the LAST base64url character
     * of the signature. For RS256/2048 that character carries 2 significant bits and 4 padding
     * bits, so {@code 'A'} → {@code 'B'} changed nothing that mattered: measured on fan's own
     * keys over 400 tokens, the decoded signature survived intact 104 times (26.0%), every one
     * of them a signature ending in {@code 'A'}. A quarter of runs therefore sent a VALID token
     * to a test asserting 401 and died five seconds later on a downstream read timeout that
     * read like flaky infrastructure — including on TASK-MONO-542's own PR, which touched
     * nothing in fan. erp and wms were fixed for this in TASK-MONO-458, finance in MONO-461,
     * scm in MONO-542; fan was the last straggler.
     *
     * <p><strong>Looped on purpose.</strong> Signing with a foreign key is deterministic, so one
     * sample would be enough to test today's implementation — but the failure mode worth
     * guarding is a future "simplification" back to a probabilistic mutation, and a
     * single-sample guard would pass on ~74% of runs against exactly that. Do not reduce this
     * to one token.
     */
    @Test
    void forgedSignatureTokenNeverVerifies() {
        int samples = 200;
        int stillValid = 0;

        for (int i = 0; i < samples; i++) {
            try {
                decodeAndVerify(helper.signForgedSignatureToken("forged-" + i));
                stillValid++;
            } catch (Exception expected) {
                // correct: signed by a key that is not in the JWKS
            }
        }

        assertThat(stillValid)
                .as("signForgedSignatureToken must never produce a verifiable token; "
                        + "%d of %d verified", stillValid, samples)
                .isZero();
    }

    /**
     * The forged token must still advertise the REAL {@code kid}. Otherwise the resource server
     * fails to select a key and rejects for the wrong reason — the integration test would stay
     * green while no longer exercising signature verification at all.
     */
    @Test
    void forgedSignatureTokenKeepsTheRealKeyIdSoTheFailureIsSignatureVerification() throws Exception {
        String forged = helper.signForgedSignatureToken("forged-kid");

        assertThat(com.nimbusds.jwt.SignedJWT.parse(forged).getHeader().getKeyID())
                .isEqualTo(helper.keyId());
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
