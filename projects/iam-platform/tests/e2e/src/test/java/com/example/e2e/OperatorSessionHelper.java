package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

/**
 * Shared helper for scenarios that need an authenticated dev SUPER_ADMIN
 * operator. Handles the enrollment-first-time path idempotently: if the
 * operator already has a TOTP row, the first login skips straight to
 * TOTP-backed login using the captured secret.
 *
 * <p>The TOTP secret discovered on first enrollment is cached in a static
 * field so subsequent tests within the same JVM can mint fresh codes
 * without re-enrolling.
 */
final class OperatorSessionHelper {

    private static final ObjectMapper M = new ObjectMapper();
    private static volatile String cachedSecret;

    private OperatorSessionHelper() {}

    /** Reset cached state — call when TOTP rows are truncated externally. */
    static synchronized void clearCachedSecret() {
        cachedSecret = null;
    }

    /** Returns a valid operator access token. Enrolls 2FA if needed. */
    static synchronized String loginAsDevSuperAdmin() throws Exception {
        // Step 1: try login without TOTP
        Response login1 = base()
                .body(Map.of(
                        "operatorId", E2EBase.DEV_OPERATOR_ID,
                        "password", E2EBase.DEV_OPERATOR_PASSWORD))
                .post("/api/admin/auth/login");

        if (login1.statusCode() == 200) {
            return M.readTree(login1.asString()).path("accessToken").asText();
        }

        JsonNode body = M.readTree(login1.asString());
        String code = body.path("code").asText();

        // If BAD_REQUEST → 2FA required but totpCode missing: use cached secret
        if ("BAD_REQUEST".equals(code) && cachedSecret != null) {
            return loginWithTotp(cachedSecret);
        }
        // TOTP enrolled+verified in a prior test class but cachedSecret lost: reset and re-enroll
        if ("BAD_REQUEST".equals(code) && cachedSecret == null) {
            resetTotpState();
            return loginAsDevSuperAdmin();
        }

        // ENROLLMENT_REQUIRED → enroll then verify then login-with-totp
        if ("ENROLLMENT_REQUIRED".equals(code)) {
            String bootstrap = body.path("bootstrapToken").asText();
            Response enroll = base()
                    .header("Authorization", "Bearer " + bootstrap)
                    .post("/api/admin/auth/2fa/enroll");
            if (enroll.statusCode() != 200) {
                throw new IllegalStateException("enroll failed: " + enroll.statusCode() + " " + enroll.asString());
            }
            JsonNode enrollBody = M.readTree(enroll.asString());
            String otpauthUri = enrollBody.path("otpauthUri").asText();
            String secret = TotpTestUtil.extractSecret(otpauthUri);
            cachedSecret = secret;

            // Enroll response includes a fresh bootstrap token for the verify step
            String verifyBootstrap = enrollBody.path("bootstrapToken").asText();

            String totp = totpWithGuard(secret);
            Response verify = base()
                    .header("Authorization", "Bearer " + verifyBootstrap)
                    .body(Map.of("totpCode", totp))
                    .post("/api/admin/auth/2fa/verify");
            if (verify.statusCode() != 200) {
                throw new IllegalStateException("verify failed: " + verify.statusCode() + " " + verify.asString());
            }
            return loginWithTotp(secret);
        }

        throw new IllegalStateException("unexpected login response: " + login1.statusCode() + " " + login1.asString());
    }

    private static String loginWithTotp(String secret) throws Exception {
        String totp = totpWithGuard(secret);
        Response r = base()
                .body(Map.of(
                        "operatorId", E2EBase.DEV_OPERATOR_ID,
                        "password", E2EBase.DEV_OPERATOR_PASSWORD,
                        "totpCode", totp))
                .post("/api/admin/auth/login");
        if (r.statusCode() != 200) {
            throw new IllegalStateException("totp login failed: " + r.statusCode() + " " + r.asString());
        }
        return M.readTree(r.asString()).path("accessToken").asText();
    }

    private static String totpWithGuard(String secret) throws InterruptedException {
        long pos = java.time.Instant.now().getEpochSecond() % 30L;
        if (pos >= 28L) {
            Thread.sleep(2_500L);
        }
        return TotpTestUtil.codeNow(secret);
    }

    private static void resetTotpState() throws Exception {
        try (Connection c = DriverManager.getConnection(
                ComposeFixture.mysqlJdbcUrl("admin_db", "admin_user", "admin_pass"));
             Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM admin_operator_totp");
            s.executeUpdate("DELETE FROM admin_operator_refresh_tokens");
        }
    }

    private static io.restassured.specification.RequestSpecification base() {
        return RestAssured.given()
                .baseUri(ComposeFixture.ADMIN_BASE_URL)
                .contentType(ContentType.JSON);
    }

    /** Returns two refresh tokens from the same account. Used by reuse-detection scenario. */
    static RefreshPair loginAndCaptureRefresh() throws Exception {
        loginAsDevSuperAdmin(); // ensure enrolled
        String secret = cachedSecret;
        if (secret == null) throw new IllegalStateException("cached secret missing — call loginAsDevSuperAdmin first");
        String totp = totpWithGuard(secret);
        Response r = base()
                .body(Map.of(
                        "operatorId", E2EBase.DEV_OPERATOR_ID,
                        "password", E2EBase.DEV_OPERATOR_PASSWORD,
                        "totpCode", totp))
                .post("/api/admin/auth/login");
        if (r.statusCode() != 200) {
            throw new IllegalStateException("refresh-pair login failed: " + r.statusCode() + " " + r.asString());
        }
        JsonNode n = M.readTree(r.asString());
        return new RefreshPair(n.path("accessToken").asText(), n.path("refreshToken").asText());
    }

    record RefreshPair(String accessToken, String refreshToken) {}
}
