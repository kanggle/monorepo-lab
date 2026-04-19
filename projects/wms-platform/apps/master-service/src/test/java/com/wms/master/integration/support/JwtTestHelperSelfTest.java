package com.wms.master.integration.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Self-tests for {@link JwtTestHelper}. Runs in the default {@code test}
 * phase — no Docker required. Validates that the helper produces signed
 * tokens and serves a JWKS document that matches the signing key.
 */
class JwtTestHelperSelfTest {

    @Test
    void issuesSignedTokenWithExpectedClaims() throws Exception {
        try (JwtTestHelper helper = JwtTestHelper.start()) {
            String token = helper.issueToken("user-123", "MASTER_READ");

            SignedJWT parsed = SignedJWT.parse(token);
            assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("user-123");
            assertThat(parsed.getJWTClaimsSet().getClaim("role")).isEqualTo("MASTER_READ");
            assertThat(parsed.getHeader().getKeyID()).isEqualTo("master-test-key");
        }
    }

    @Test
    void supportsMultipleRolesAsArrayClaim() throws Exception {
        try (JwtTestHelper helper = JwtTestHelper.start()) {
            String token = helper.issueToken("user-xyz",
                    List.of("MASTER_READ", "MASTER_WRITE"),
                    java.time.Duration.ofMinutes(5));

            SignedJWT parsed = SignedJWT.parse(token);
            Object role = parsed.getJWTClaimsSet().getClaim("role");
            assertThat(role).isInstanceOf(List.class);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) role;
            assertThat(roles).containsExactly("MASTER_READ", "MASTER_WRITE");
        }
    }

    @Test
    void jwksEndpointServesPublicKey() throws Exception {
        try (JwtTestHelper helper = JwtTestHelper.start()) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(helper.jwkSetUri())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body())
                    .contains("\"kty\":\"RSA\"")
                    .contains("\"kid\":\"master-test-key\"")
                    .contains("\"use\":\"sig\"");
        }
    }
}
