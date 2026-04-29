package com.wms.gateway.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.Test;

/**
 * No-Docker self-test for {@link JwtTestHelper}. Covered by the regular
 * {@code ./gradlew :...:gateway-service:test} task so that even before the
 * Testcontainers path is runnable, we know the helper produces
 * correctly-shaped, cryptographically valid tokens against the matching JWKS.
 */
class JwtTestHelperTest {

    private final JwtTestHelper helper = new JwtTestHelper();

    @Test
    void tokenParsesAndVerifiesAgainstItsOwnJwks() throws Exception {
        String token = helper.signMasterWriteToken("user-42");

        JWTClaimsSet claims = decodeAndVerify(token);

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.getStringClaim("role")).isEqualTo("MASTER_WRITE");
        assertThat(claims.getStringListClaim("roles"))
                .containsExactlyInAnyOrder("MASTER_WRITE", "MASTER_READ");
        assertThat(claims.getStringClaim("email")).isEqualTo("user-42@test.local");
        assertThat(claims.getStringClaim("account_type")).isEqualTo("OPERATOR");
        assertThat(claims.getAudience()).containsExactly("wms");
        assertThat(claims.getIssuer()).isEqualTo("https://test.local/issuer");
        assertThat(claims.getExpirationTime()).isAfter(new java.util.Date());
    }

    @Test
    void readTokenHasReadOnlyRoles() throws Exception {
        String token = helper.signMasterReadToken("reader-1");
        JWTClaimsSet claims = decodeAndVerify(token);
        assertThat(claims.getStringClaim("role")).isEqualTo("MASTER_READ");
        assertThat(claims.getStringListClaim("roles")).containsExactly("MASTER_READ");
        assertThat(claims.getStringClaim("account_type")).isEqualTo("OPERATOR");
        assertThat(claims.getAudience()).containsExactly("wms");
    }

    @Test
    void tokenSignedByOneHelperDoesNotVerifyAgainstAnother() throws Exception {
        JwtTestHelper other = new JwtTestHelper();
        String token = helper.signToken("u", "R", 300);

        ConfigurableJWTProcessor<SecurityContext> processor = buildProcessor(other.jwksJson());

        assertThatThrownBy(() -> processor.process(token, null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void jwksJsonExposesTheSameKidAsSignedTokens() throws Exception {
        JWKSet set = JWKSet.parse(helper.jwksJson());
        assertThat(set.getKeys()).hasSize(1);
        RSAKey rsa = (RSAKey) set.getKeys().get(0);
        // Public JWK must not leak private parameters.
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
