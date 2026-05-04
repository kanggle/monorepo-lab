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
