package com.example.community.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-253 regression tests covering the OIDC migration acceptance criteria:
 *
 * <ul>
 *   <li>Legacy {@code POST /api/auth/login} tokens (iss=global-account-platform)
 *       still pass — D2-b deprecation compatibility.</li>
 *   <li>SAS-issued tokens (iss=oidc.issuer-url) pass — primary path.</li>
 *   <li>Cross-tenant tokens (tenant_id=wms) are rejected with 403 TENANT_FORBIDDEN.</li>
 *   <li>Missing Authorization header returns 401.</li>
 * </ul>
 */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("TASK-BE-253 OIDC 인증 회귀 통합 테스트")
class OidcAuthIntegrationTest extends CommunityIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    private void stubAccountProfile(String accountId, String displayName) {
        ACCOUNT_WM.stubFor(get(urlPathMatching("/internal/accounts/.*/profile"))
                .willReturn(okJson("{\"accountId\":\"" + accountId
                        + "\",\"displayName\":\"" + displayName + "\"}")));
    }

    private static final String PUBLISH_BODY = """
            {"type":"ARTIST_POST","visibility":"PUBLIC","title":"hi","body":"world","mediaUrls":[]}
            """;

    @Test
    @DisplayName("legacy iss=global-account-platform 토큰 → 201 (호환성)")
    void legacyToken_returns201() throws Exception {
        stubAccountProfile("artist", "Test Artist");
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearerToken(artistId, List.of("ARTIST")))
                        .contentType("application/json")
                        .content(PUBLISH_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("SAS issuer 토큰 → 201 (표준 경로)")
    void sasToken_returns201() throws Exception {
        stubAccountProfile("artist", "Test Artist");
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 20);

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearerSasToken(artistId, List.of("ARTIST")))
                        .contentType("application/json")
                        .content(PUBLISH_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("cross-tenant 토큰 (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void crossTenantWmsToken_returns403() throws Exception {
        String wmsUserId = "wms-user-" + UUID.randomUUID().toString().substring(0, 20);

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearerTokenWithTenant(wmsUserId, List.of("ARTIST"), "wms"))
                        .contentType("application/json")
                        .content(PUBLISH_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("Authorization 헤더 없음 → 401")
    void missingAuthorization_returns401() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .contentType("application/json")
                        .content(PUBLISH_BODY))
                .andExpect(status().isUnauthorized());
    }
}
