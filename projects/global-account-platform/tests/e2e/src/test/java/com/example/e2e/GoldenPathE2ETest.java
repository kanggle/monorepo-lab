package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * Golden path E2E (TASK-BE-041c §2):
 *   1. JWKS fetch
 *   2. login → ENROLLMENT_REQUIRED + bootstrapToken
 *   3. 2fa enroll → otpauthUri + recoveryCodes
 *   4. derive TOTP from otpauthUri
 *   5. 2fa verify → 200
 *   6. login with totp → access+refresh
 *   7. refresh → new pair
 *   8. logout → 204
 *   9. revoked access token on protected endpoint → 401 TOKEN_REVOKED
 */
class GoldenPathE2ETest extends E2EBase {

    @BeforeAll
    static void resetTotpState() throws Exception {
        try (Connection c = DriverManager.getConnection(
                ComposeFixture.mysqlJdbcUrl("admin_db", "admin_user", "admin_pass"));
             Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM admin_operator_totp");
            s.executeUpdate("DELETE FROM admin_operator_refresh_tokens");
        }
        OperatorSessionHelper.clearCachedSecret();
    }

    @Test
    @DisplayName("운영자 최초 가입부터 logout 후 access 토큰 revoke 확인까지 전 구간 성공")
    void golden_path_full_flow() throws Exception {
        // 1. JWKS fetch
        Response jwks = admin().when().get("/.well-known/admin/jwks.json").then().extract().response();
        assertThat(jwks.statusCode()).isEqualTo(200);
        JsonNode jwksJson = MAPPER.readTree(jwks.asString());
        assertThat(jwksJson.path("keys").isArray()).isTrue();
        assertThat(jwksJson.path("keys").size()).isGreaterThanOrEqualTo(1);

        // 2. first login → ENROLLMENT_REQUIRED (dev operator has no TOTP row yet)
        Response firstLogin = admin()
                .body(Map.of("operatorId", DEV_OPERATOR_ID, "password", DEV_OPERATOR_PASSWORD))
                .post("/api/admin/auth/login");
        assertThat(firstLogin.statusCode()).isEqualTo(401);
        JsonNode enrollResp = MAPPER.readTree(firstLogin.asString());
        assertThat(enrollResp.path("code").asText()).isEqualTo("ENROLLMENT_REQUIRED");
        String bootstrapToken = enrollResp.path("bootstrapToken").asText();
        assertThat(bootstrapToken).isNotBlank();

        // 3. enroll
        Response enroll = admin()
                .header("Authorization", "Bearer " + bootstrapToken)
                .post("/api/admin/auth/2fa/enroll");
        assertThat(enroll.statusCode()).isEqualTo(200);
        JsonNode enrollBody = MAPPER.readTree(enroll.asString());
        String otpauthUri = enrollBody.path("otpauthUri").asText();
        assertThat(otpauthUri).startsWith("otpauth://totp/");
        assertThat(enrollBody.path("recoveryCodes").isArray()).isTrue();
        assertThat(enrollBody.path("recoveryCodes").size()).isEqualTo(10);

        // 4. derive TOTP secret + current code
        String secret = TotpTestUtil.extractSecret(otpauthUri);

        // 5. verify — enroll response includes a fresh bootstrap token for the verify step
        String verifyBootstrap = enrollBody.path("bootstrapToken").asText();
        assertThat(verifyBootstrap).isNotBlank();

        String totp = computeTotpWithRetry(secret);
        Response verify = admin()
                .header("Authorization", "Bearer " + verifyBootstrap)
                .body(Map.of("totpCode", totp))
                .post("/api/admin/auth/2fa/verify");
        assertThat(verify.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(verify.asString()).path("verified").asBoolean()).isTrue();

        // 6. login with TOTP
        String totp2 = computeTotpWithRetry(secret);
        Response fullLogin = admin()
                .body(Map.of("operatorId", DEV_OPERATOR_ID, "password", DEV_OPERATOR_PASSWORD, "totpCode", totp2))
                .post("/api/admin/auth/login");
        assertThat(fullLogin.statusCode()).isEqualTo(200);
        JsonNode tokens = MAPPER.readTree(fullLogin.asString());
        String accessToken = tokens.path("accessToken").asText();
        String refreshToken = tokens.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        // 7. refresh
        Response refreshed = admin()
                .body(Map.of("refreshToken", refreshToken))
                .post("/api/admin/auth/refresh");
        assertThat(refreshed.statusCode()).isEqualTo(200);
        JsonNode newTokens = MAPPER.readTree(refreshed.asString());
        String newAccess = newTokens.path("accessToken").asText();
        String newRefresh = newTokens.path("refreshToken").asText();
        assertThat(newAccess).isNotBlank().isNotEqualTo(accessToken);
        assertThat(newRefresh).isNotBlank().isNotEqualTo(refreshToken);

        // 8. logout
        Response logout = admin()
                .header("Authorization", "Bearer " + newAccess)
                .post("/api/admin/auth/logout");
        assertThat(logout.statusCode()).isEqualTo(204);

        // 9. protected endpoint with revoked access → 401 TOKEN_REVOKED
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            Response protectedCall = admin()
                    .header("Authorization", "Bearer " + newAccess)
                    .header("X-Operator-Reason", "smoke-check")
                    .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                    .body(Map.of("accountIds", java.util.List.of("acc-does-not-matter"),
                            "reason", "post-logout-smoke-check-8-char"))
                    .post("/api/admin/accounts/bulk-lock");
            assertThat(protectedCall.statusCode()).isEqualTo(401);
            JsonNode err = MAPPER.readTree(protectedCall.asString());
            assertThat(err.path("code").asText()).isEqualTo("TOKEN_REVOKED");
        });
    }

    /** TOTP boundary guard: if we're in the last few hundred ms of a step, wait for the next one. */
    private static String computeTotpWithRetry(String secret) throws InterruptedException {
        long epochSec = java.time.Instant.now().getEpochSecond();
        long posInStep = epochSec % 30L;
        if (posInStep >= 28L) {
            Thread.sleep(2_500L); // roll into next step
        }
        return TotpTestUtil.codeNow(secret);
    }
}
