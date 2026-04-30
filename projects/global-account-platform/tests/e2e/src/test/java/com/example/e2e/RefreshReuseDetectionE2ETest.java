package com.example.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh-token reuse detection E2E (TASK-BE-041c §4).
 *
 *   1. login → refreshToken_0
 *   2. refresh → refreshToken_1 (token_0 now REVOKED with ROTATED)
 *   3. reuse refreshToken_0 → 401 REFRESH_TOKEN_REUSE_DETECTED
 *   4. subsequent refreshToken_1 → 401 (chain invalidated)
 */
class RefreshReuseDetectionE2ETest extends E2EBase {

    @Test
    @DisplayName("refresh 재사용 탐지 → 전체 체인 revoke")
    void refresh_reuse_detection_invalidates_chain() throws Exception {
        OperatorSessionHelper.RefreshPair pair = OperatorSessionHelper.loginAndCaptureRefresh();
        String refreshToken0 = pair.refreshToken();

        // 2. rotate → refreshToken_1
        Response rot = admin()
                .body(Map.of("refreshToken", refreshToken0))
                .post("/api/admin/auth/refresh");
        assertThat(rot.statusCode()).isEqualTo(200);
        String refreshToken1 = MAPPER.readTree(rot.asString()).path("refreshToken").asText();
        assertThat(refreshToken1).isNotBlank().isNotEqualTo(refreshToken0);

        // 3. reuse refreshToken_0 → 401 REFRESH_TOKEN_REUSE_DETECTED
        Response reuse = admin()
                .body(Map.of("refreshToken", refreshToken0))
                .post("/api/admin/auth/refresh");
        assertThat(reuse.statusCode()).isEqualTo(401);
        JsonNode reuseBody = MAPPER.readTree(reuse.asString());
        assertThat(reuseBody.path("code").asText()).isEqualTo("REFRESH_TOKEN_REUSE_DETECTED");

        // 4. refreshToken_1 should also be revoked now
        Response followup = admin()
                .body(Map.of("refreshToken", refreshToken1))
                .post("/api/admin/auth/refresh");
        assertThat(followup.statusCode()).isEqualTo(401);
        String followupCode = MAPPER.readTree(followup.asString()).path("code").asText();
        // Implementation may report REFRESH_TOKEN_REUSE_DETECTED or INVALID_REFRESH_TOKEN
        // (already-revoked row). Accept either — the business property is "chain revoked".
        assertThat(followupCode).isIn("REFRESH_TOKEN_REUSE_DETECTED", "INVALID_REFRESH_TOKEN");
    }
}
