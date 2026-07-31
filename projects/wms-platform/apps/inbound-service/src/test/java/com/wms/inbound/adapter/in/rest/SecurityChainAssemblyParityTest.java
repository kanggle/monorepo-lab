package com.wms.inbound.adapter.in.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.example.security.oauth2.TenantClaimValidator;
import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.in.CancelAsnUseCase;
import com.wms.inbound.application.port.in.CloseAsnUseCase;
import com.wms.inbound.application.port.in.QueryAsnUseCase;
import com.wms.inbound.application.port.in.ReceiveAsnUseCase;
import com.wms.inbound.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Behaviour-parity net for inbound-service's adoption of
 * {@code ResourceServerChainAssembler} (ADR-MONO-058 § D4, TASK-BE-569 AC-4).
 *
 * <p>Every assertion below is driven through the <em>real</em> {@link SecurityConfig}
 * filter chain. The two that would catch a wrong builder default are
 * {@link #unmappedPath_withValidToken_isNotDenied()} (the assembler defaults
 * {@code anyRequest()} to {@code denyAll()}; inbound-service's tail is
 * {@code authenticated()}) and {@link #logoutPath_isNotHandledByALogoutFilter()} (the
 * assembler does not disable {@code LogoutFilter}; the pre-D4 chain did).
 */
@WebMvcTest(controllers = AsnController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
@DisplayName("D4 security-chain assembly 동작 동등성 (inbound-service)")
class SecurityChainAssemblyParityTest {

    private static final String UNMAPPED_PATH = "/__d4_probe__/not-a-route";
    private static final String ASNS = "/api/v1/inbound/asns";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiveAsnUseCase receiveAsnUseCase;

    @MockitoBean
    private CancelAsnUseCase cancelAsnUseCase;

    @MockitoBean
    private CloseAsnUseCase closeAsnUseCase;

    @MockitoBean
    private QueryAsnUseCase queryAsnUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ─── 401 vs 403 boundary (the wms-specific entry point, TASK-MONO-019) ────

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED 봉투")
    void missingToken_returns401() throws Exception {
        mockMvc.perform(get(ASNS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("tenant_mismatch 검증 실패 → 403 TENANT_FORBIDDEN 봉투 (401 아님)")
    void tenantMismatch_returns403TenantForbidden() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "tenant mismatch",
                List.of(new OAuth2Error(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH,
                        "Cross-tenant access denied", null))));

        mockMvc.perform(get(ASNS).header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("issuer 등 그 외 검증 실패 → 401 UNAUTHORIZED (403 으로 새지 않는다)")
    void otherValidationFailure_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "bad issuer",
                List.of(new OAuth2Error("invalid_issuer", "Issuer not allowed", null))));

        mockMvc.perform(get(ASNS).header("Authorization", "Bearer x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─── authorization boundary (method-level; this chain has no URL role gate) ──

    @Test
    @DisplayName("INBOUND_READ 토큰 → 컨트롤러 도달")
    void authorizedToken_reachesController() throws Exception {
        // Mockito's default-empty-values answer knows List but not the shared
        // PageResult record, so this must be stubbed explicitly (previously
        // the unstubbed List<AsnSummaryResult> return defaulted to empty).
        when(queryAsnUseCase.list(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get(ASNS)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INBOUND_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("권한 없는 토큰 → 403 FORBIDDEN 봉투")
    void insufficientRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get(ASNS)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ERP webhook 은 public path 다 — 토큰 없이도 401/403 이 아니다")
    void erpWebhook_isPublic() throws Exception {
        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "/webhooks/erp/asn must bypass auth, got " + status;
                });
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
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("LogoutFilter 는 비활성 — POST /logout 은 302 가 아니라 401")
    void logoutPath_isNotHandledByALogoutFilter() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
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
