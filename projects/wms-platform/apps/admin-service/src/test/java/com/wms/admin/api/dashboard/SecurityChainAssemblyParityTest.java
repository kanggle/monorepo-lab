package com.wms.admin.api.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.security.oauth2.TenantClaimValidator;
import com.wms.admin.api.advice.GlobalExceptionHandler;
import com.wms.admin.config.SecurityConfig;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import com.wms.admin.readmodel.inventory.InventorySnapshotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Behaviour-parity net for admin-service's adoption of
 * {@code ResourceServerChainAssembler} (ADR-MONO-058 § D4, TASK-BE-569 AC-3/AC-4).
 *
 * <p>admin-service is the divergent one of the five: it declares a {@code RoleHierarchy}
 * bean and its {@code jwtAuthenticationConverter} synthesises {@code ROLE_WMS_VIEWER}
 * from the verified {@code entitled_domains} claim. Both of those are load-bearing
 * <em>through the assembled chain</em>, and neither is provable with a
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} post-processor — that builds an
 * {@code Authentication} directly and never runs the chain's converter. The two tests
 * that matter therefore go through a mocked {@link JwtDecoder} and a real
 * {@code Authorization: Bearer} header, so the chain's own converter and the role
 * hierarchy decide the outcome:
 *
 * <ul>
 *   <li>{@link #entitlementOnlyToken_reachesViewerDashboard()} — AC-3, the
 *       entitlement-trust dual-accept, end-to-end rather than at converter-unit level.</li>
 *   <li>{@link #roleHierarchy_adminSatisfiesTheViewerGate()} — the {@code RoleHierarchy}
 *       bean still reaching method security after the chain was reassembled.</li>
 * </ul>
 */
@WebMvcTest(controllers = InventoryDashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("D4 security-chain assembly 동작 동등성 (admin-service)")
class SecurityChainAssemblyParityTest {

    private static final String UNMAPPED_PATH = "/__d4_probe__/not-a-route";
    private static final String DASHBOARD = "/api/v1/admin/dashboard/inventory";
    private static final Instant NOW = Instant.parse("2026-05-09T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventorySnapshotRepository repository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void stubOnePage() {
        Page<InventorySnapshotEntity> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(repository.search(any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(page);
    }

    /** A verified token as the chain's own converter would see it. */
    private void decoderReturns(Consumer<Jwt.Builder> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("actor-1")
                .issuedAt(NOW.minusSeconds(60))
                .expiresAt(NOW.plusSeconds(600));
        claims.accept(builder);
        when(jwtDecoder.decode(anyString())).thenReturn(builder.build());
    }

    // ─── 401 vs 403 boundary (the wms-specific entry point, TASK-MONO-019) ────

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED 봉투")
    void missingToken_returns401() throws Exception {
        mockMvc.perform(get(DASHBOARD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("tenant_mismatch 검증 실패 → 403 TENANT_FORBIDDEN 봉투 (401 아님)")
    void tenantMismatch_returns403TenantForbidden() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "tenant mismatch",
                List.of(new OAuth2Error(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH,
                        "Cross-tenant access denied", null))));

        mockMvc.perform(get(DASHBOARD).header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("issuer 등 그 외 검증 실패 → 401 UNAUTHORIZED (403 으로 새지 않는다)")
    void otherValidationFailure_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "bad issuer",
                List.of(new OAuth2Error("invalid_issuer", "Issuer not allowed", null))));

        mockMvc.perform(get(DASHBOARD).header("Authorization", "Bearer x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── admin-service's two service-specific authorization mechanisms ────────

    @Test
    @DisplayName("AC-3 entitlement-trust: entitled_domains ∋ wms + 역할 없음 → VIEWER 대시보드 200")
    void entitlementOnlyToken_reachesViewerDashboard() throws Exception {
        stubOnePage();
        decoderReturns(b -> b.claim("tenant_id", "globex-corp")
                .claim("entitled_domains", List.of("wms")));

        mockMvc.perform(get(DASHBOARD).header("Authorization", "Bearer x"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RoleHierarchy: WMS_ADMIN 토큰이 hasRole('WMS_VIEWER') 게이트를 통과한다")
    void roleHierarchy_adminSatisfiesTheViewerGate() throws Exception {
        stubOnePage();
        decoderReturns(b -> b.claim("tenant_id", "wms").claim("roles", List.of("WMS_ADMIN")));

        mockMvc.perform(get(DASHBOARD).header("Authorization", "Bearer x"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("역할도 entitlement 도 없는 토큰 → 403 FORBIDDEN (fail-closed)")
    void neitherRoleNorEntitlement_returns403() throws Exception {
        decoderReturns(b -> b.claim("tenant_id", "acme")
                .claim("entitled_domains", List.of("finance")));

        mockMvc.perform(get(DASHBOARD).header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("VIEWER 권한 토큰 → 대시보드 200 (기존 슬라이스 테스트와 동일한 기준선)")
    void viewerAuthority_reachesDashboard() throws Exception {
        stubOnePage();
        mockMvc.perform(get(DASHBOARD)
                        .with(jwt().jwt(j -> j.claims(c -> c.putAll(Map.of("tenant_id", "wms"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_WMS_VIEWER"))))
                .andExpect(status().isOk());
    }

    // ─── the divergences this service preserves ───────────────────────────────

    @Test
    @DisplayName("anyRequest() 꼬리는 authenticated() 이다 — 미매핑 경로도 인증만 되면 denyAll 되지 않는다")
    void unmappedPath_withValidToken_isNotDenied() throws Exception {
        mockMvc.perform(get(UNMAPPED_PATH).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("미매핑 경로 + 토큰 없음 → 여전히 401 (열려 있지 않다)")
    void unmappedPath_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(UNMAPPED_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("LogoutFilter 는 비활성 — POST /logout 은 302 가 아니라 401")
    void logoutPath_isNotHandledByALogoutFilter() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("public path 는 토큰 없이 통과한다 (PublicPaths.asSet() 경유)")
    void publicPath_bypassesAuth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(result -> {
            int status = result.getResponse().getStatus();
            assert status != 401 : "actuator/health must bypass auth, got " + status;
        });
    }
}
