package com.wms.inventory.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.security.oauth2.TenantClaimValidator;
import com.wms.inventory.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inventory.application.port.in.QueryTransferUseCase;
import com.wms.inventory.application.port.in.TransferStockUseCase;
import com.wms.inventory.config.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Behaviour-parity net for inventory-service's adoption of
 * {@code ResourceServerChainAssembler} (ADR-MONO-058 § D4, TASK-BE-569 AC-4).
 *
 * <p>Every assertion below is driven through the <em>real</em> {@link SecurityConfig}
 * filter chain — a unit test of the builder call would prove only that the builder was
 * called, which is not the property at risk. The property at risk is that the assembled
 * chain answers each request exactly as the hand-written chain did.
 *
 * <h2>The two assertions that would catch a wrong default</h2>
 *
 * <ul>
 *   <li>{@link #unmappedPath_withValidToken_isNotDenied()} — the assembler defaults its
 *       {@code anyRequest()} tail to {@code denyAll()}; inventory-service's tail is
 *       {@code authenticated()}. If the {@code anyRequestAuthenticated()} call is ever
 *       dropped, this test — and nothing else in the suite — goes red.</li>
 *   <li>{@link #logoutPath_isNotHandledByALogoutFilter()} — the assembler does not
 *       disable Spring Security's default {@code LogoutFilter}; the pre-D4 chain did. If
 *       the service-local {@code .logout(disable)} is ever dropped, {@code /logout}
 *       silently starts answering 302 instead of 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = TransferController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("D4 security-chain assembly 동작 동등성 (inventory-service)")
class SecurityChainAssemblyParityTest {

    private static final String UNMAPPED_PATH = "/__d4_probe__/not-a-route";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferStockUseCase transferStock;

    @MockitoBean
    private QueryTransferUseCase queryTransfer;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    // ─── 401 vs 403 boundary (the wms-specific entry point, TASK-MONO-019) ────

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED 봉투")
    void missingToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/transfers"))
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

        mockMvc.perform(get("/api/v1/inventory/transfers").header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("issuer 등 그 외 검증 실패 → 401 UNAUTHORIZED (403 으로 새지 않는다)")
    void otherValidationFailure_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "bad issuer",
                List.of(new OAuth2Error("invalid_issuer", "Issuer not allowed", null))));

        mockMvc.perform(get("/api/v1/inventory/transfers").header("Authorization", "Bearer x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ─── authorization boundary ───────────────────────────────────────────────

    @Test
    @DisplayName("권한 있는 토큰 → 컨트롤러 도달 (필터체인·메서드보안 모두 통과)")
    void authorizedToken_reachesController() throws Exception {
        // queryTransfer.findById(..) returns Optional.empty() by Mockito default →
        // TransferNotFoundException → 404. A 404 proves the request got past the
        // filter chain AND past @PreAuthorize into the controller body.
        mockMvc.perform(get("/api/v1/inventory/transfers/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INVENTORY_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
    }

    @Test
    @DisplayName("권한 없는 토큰 → 403 FORBIDDEN 봉투")
    void insufficientRole_returns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/transfers/" + UUID.randomUUID())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
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
