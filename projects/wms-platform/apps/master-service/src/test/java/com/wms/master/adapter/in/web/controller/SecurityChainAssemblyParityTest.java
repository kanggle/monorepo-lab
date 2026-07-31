package com.wms.master.adapter.in.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.page.PageResult;
import com.example.security.oauth2.TenantClaimValidator;
import com.wms.master.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.master.application.port.in.WarehouseCrudUseCase;
import com.wms.master.application.port.in.WarehouseQueryUseCase;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Behaviour-parity net for master-service's adoption of
 * {@code ResourceServerChainAssembler} (ADR-MONO-058 § D4, TASK-BE-569 AC-4).
 *
 * <p>Every assertion below is driven through the <em>real</em> {@link SecurityConfig}
 * filter chain. The two that would catch a wrong builder default are
 * {@link #unmappedPath_withValidToken_isNotDenied()} (the assembler defaults
 * {@code anyRequest()} to {@code denyAll()}; master-service's tail is
 * {@code authenticated()}) and {@link #logoutPath_isNotHandledByALogoutFilter()} (the
 * assembler does not disable {@code LogoutFilter}; the pre-D4 chain did).
 */
@WebMvcTest(controllers = WarehouseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("D4 security-chain assembly 동작 동등성 (master-service)")
class SecurityChainAssemblyParityTest {

    private static final String UNMAPPED_PATH = "/__d4_probe__/not-a-route";
    private static final String WAREHOUSES = "/api/v1/master/warehouses";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WarehouseCrudUseCase crudUseCase;

    @MockitoBean
    private WarehouseQueryUseCase queryUseCase;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void stubEmptyList() {
        when(queryUseCase.list(any(ListWarehousesQuery.class)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
    }

    // ─── 401 vs 403 boundary (the wms-specific entry point, TASK-MONO-019) ────

    @Test
    @DisplayName("토큰 없음 → 401 UNAUTHORIZED 봉투")
    void missingToken_returns401() throws Exception {
        mockMvc.perform(get(WAREHOUSES))
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

        mockMvc.perform(get(WAREHOUSES).header("Authorization", "Bearer x"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("issuer 등 그 외 검증 실패 → 401 UNAUTHORIZED (403 으로 새지 않는다)")
    void otherValidationFailure_returns401() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new JwtValidationException(
                "bad issuer",
                List.of(new OAuth2Error("invalid_issuer", "Issuer not allowed", null))));

        mockMvc.perform(get(WAREHOUSES).header("Authorization", "Bearer x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ─── authorization boundary ───────────────────────────────────────────────

    @Test
    @DisplayName("역할 있는 토큰 → 컨트롤러 도달")
    void authorizedToken_reachesController() throws Exception {
        stubEmptyList();
        mockMvc.perform(get(WAREHOUSES).with(jwt().jwt(b -> b.claim("role", "MASTER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("master-service 체인에는 URL 레벨 role gate 가 없다 — 역할 없는 토큰도 컨트롤러에 도달한다"
            + " (인가는 애플리케이션 계층 책임)")
    void noUrlLevelRoleGate_roleLessTokenStillReachesController() throws Exception {
        stubEmptyList();
        mockMvc.perform(get(WAREHOUSES).with(jwt()))
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
