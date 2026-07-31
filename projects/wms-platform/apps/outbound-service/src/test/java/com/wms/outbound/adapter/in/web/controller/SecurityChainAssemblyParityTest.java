package com.wms.outbound.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.security.oauth2.TenantClaimValidator;
import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.application.port.in.CancelOrderUseCase;
import com.wms.outbound.application.port.in.ReceiveOrderUseCase;
import com.wms.outbound.config.SecurityConfig;
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
 * Behaviour-parity net for outbound-service's adoption of
 * {@code ResourceServerChainAssembler} (ADR-MONO-058 § D4, TASK-BE-569 AC-4).
 *
 * <p>outbound-service is the one wms service whose filter chain carries
 * <strong>URL-level role gates</strong>, so it is the one whose adoption exercises the
 * assembler's {@code authorizeRules(...)} seam and the rule ordering that seam promises:
 * public paths first, the service's own rules next, the {@code anyRequest()} tail last.
 * {@link #getUnderApi_withoutOutboundRole_returns403()} and
 * {@link #getUnderApi_withReadRole_passesTheGate()} are the two sides of that gate;
 * {@link #erpWebhook_isPublicDespiteTheRoleGates()} pins that the public paths are still
 * registered <em>before</em> it.
 */
@WebMvcTest(controllers = OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json"
})
@DisplayName("D4 security-chain assembly 동작 동등성 (outbound-service)")
class SecurityChainAssemblyParityTest {

    private static final String UNMAPPED_PATH = "/__d4_probe__/not-a-route";
    private static final String ORDERS = "/api/v1/outbound/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiveOrderUseCase receiveOrder;

    @MockitoBean
    private CancelOrderUseCase cancelOrder;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ─── 401 vs 403 boundary (the wms-specific entry point, TASK-MONO-019) ────

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED 봉투")
    void missingToken_returns401() throws Exception {
        mockMvc.perform(get(ORDERS + "/any"))
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

        mockMvc.perform(get(ORDERS + "/any").header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("issuer 등 그 외 검증 실패 → 401 UNAUTHORIZED (403 으로 새지 않는다)")
    void otherValidationFailure_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "bad issuer",
                List.of(new OAuth2Error("invalid_issuer", "Issuer not allowed", null))));

        mockMvc.perform(get(ORDERS + "/any").header("Authorization", "Bearer x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─── the URL-level role gates (authorizeRules seam) ───────────────────────

    @Test
    @DisplayName("GET /api/** + OUTBOUND 역할 없음 → 403 FORBIDDEN (URL 레벨 게이트가 살아 있다)")
    void getUnderApi_withoutOutboundRole_returns403() throws Exception {
        mockMvc.perform(get(ORDERS + "/any")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/** + OUTBOUND_READ → 게이트 통과 (핸들러가 없어 404)")
    void getUnderApi_withReadRole_passesTheGate() throws Exception {
        mockMvc.perform(get(ORDERS + "/any")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OUTBOUND_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/** 는 WRITE 가 필요하다 — READ 토큰은 403")
    void postUnderApi_withOnlyReadRole_returns403() throws Exception {
        mockMvc.perform(post(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OUTBOUND_READ"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("ERP webhook 은 role gate 보다 먼저 등록된 public path 다 — 토큰 없이도 401/403 이 아니다")
    void erpWebhook_isPublicDespiteTheRoleGates() throws Exception {
        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assert status != 401 && status != 403
                            : "/webhooks/erp/order must bypass auth, got " + status;
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
