package com.example.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workload-identity e2e (TASK-BE-321, ADR-005 단계 4 검증 정합).
 *
 * <p>Proves the GAP {@code client_credentials} Bearer-JWT inter-service chain end-to-end against the
 * docker-compose.e2e.yml topology — the path the migration (BE-317…319b) established but that no
 * prior {@code @Tag("smoke")} test exercised ({@link GoldenPathE2ETest} is entirely admin
 * operator-auth and never reaches account/security {@code /internal/**} with a workload Bearer).
 *
 * <p>Chain under test, all aligned to {@code OIDC_ISSUER_URL=http://auth-service:8081}:
 * <ol>
 *   <li>auth-service (SAS) mints a client_credentials access token via {@code POST /oauth2/token};
 *       its {@code iss} claim = {@code http://auth-service:8081}.</li>
 *   <li>account-service / security-service receivers validate signature (JWKS fetched from
 *       {@code http://auth-service:8081/oauth2/jwks}) + issuer (== token iss). No bypass in the
 *       {@code e2e} profile, so {@code /internal/**} is fail-closed.</li>
 * </ol>
 *
 * <p>The discriminator for "the receiver actually validated the JWT" is <b>404 (not 401)</b> on the
 * account status probe: 401 would mean the Bearer was rejected (issuer/JWKS misalignment), 404 means
 * the JWT was accepted and the request reached business logic for a non-existent account.
 */
@Tag("smoke")
@DisplayName("TASK-BE-321: inter-service client_credentials Bearer JWT validated by account/security /internal/**")
class InternalWorkloadAuthE2ETest extends E2EBase {

    /** Mints a GAP client_credentials access token for the given workload client (secret = "secret", V0019). */
    private static String mintWorkloadToken(String clientId) {
        Response token = RestAssured.given()
                .baseUri(ComposeFixture.AUTH_BASE_URL)
                .auth().preemptive().basic(clientId, "secret")
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "client_credentials")
                .formParam("scope", "internal.invoke")
                .post("/oauth2/token");

        assertThat(token.statusCode())
                .as("client_credentials token issuance for %s", clientId)
                .isEqualTo(200);
        String accessToken = token.jsonPath().getString("access_token");
        assertThat(accessToken).as("access_token present").isNotBlank();
        return accessToken;
    }

    @Test
    @DisplayName("account /internal/**: 무인증 401 (fail-closed), 유효 GAP JWT 404 (검증 통과)")
    void account_internal_requires_valid_gap_jwt() {
        String accountId = UUID.randomUUID().toString();

        // (a) no credentials → fail-closed 401 (e2e profile does not bypass)
        Response noAuth = RestAssured.given()
                .baseUri(ComposeFixture.ACCOUNT_BASE_URL)
                .get("/internal/accounts/{id}/status", accountId);
        assertThat(noAuth.statusCode())
                .as("account /internal/** rejects unauthenticated request")
                .isEqualTo(401);

        // (b) valid GAP client_credentials Bearer → JWT accepted, business logic returns 404 (unknown id).
        //     404 (not 401) is the proof the receiver validated signature + issuer against auth-service JWKS.
        String token = mintWorkloadToken("admin-service-client");
        Response withJwt = RestAssured.given()
                .baseUri(ComposeFixture.ACCOUNT_BASE_URL)
                .header("Authorization", "Bearer " + token)
                .get("/internal/accounts/{id}/status", accountId);
        assertThat(withJwt.statusCode())
                .as("account /internal/** accepts a valid GAP JWT (404 = reached business logic, not 401)")
                .isEqualTo(404);
    }

    @Test
    @DisplayName("security /internal/security/**: 무인증 403 (fail-closed), 유효 GAP JWT 200")
    void security_internal_requires_valid_gap_jwt() {
        String accountId = UUID.randomUUID().toString();

        // (a) no credentials → fail-closed. security-service's InternalAuthFilter rejects with
        //     403 PERMISSION_DENIED (its established, contract-preserved status — BE-319a), whereas
        //     account-service's oauth2ResourceServer entry point answers 401. Both are fail-closed;
        //     the per-service status difference is intentional.
        Response noAuth = RestAssured.given()
                .baseUri(ComposeFixture.SECURITY_BASE_URL)
                .get("/internal/security/login-history?accountId={id}", accountId);
        assertThat(noAuth.statusCode())
                .as("security /internal/security/** rejects unauthenticated request (403 PERMISSION_DENIED)")
                .isEqualTo(403);

        // (b) valid GAP Bearer → 200 with an empty page for an unknown account
        String token = mintWorkloadToken("admin-service-client");
        Response withJwt = RestAssured.given()
                .baseUri(ComposeFixture.SECURITY_BASE_URL)
                .accept(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .get("/internal/security/login-history?accountId={id}", accountId);
        assertThat(withJwt.statusCode())
                .as("security /internal/security/** accepts a valid GAP JWT")
                .isEqualTo(200);
        assertThat(withJwt.jsonPath().getList("content"))
                .as("empty login-history page for unknown account")
                .isEmpty();
    }
}
