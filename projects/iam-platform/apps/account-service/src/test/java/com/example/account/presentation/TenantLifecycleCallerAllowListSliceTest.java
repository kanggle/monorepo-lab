package com.example.account.presentation;

import com.example.account.application.result.TenantPageResult;
import com.example.account.application.result.TenantResult;
import com.example.account.application.service.TenantProvisionUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.TenantLifecycleController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-640 — the tenant lifecycle caller allow-list with the dev bypass OFF (the production chain).
 *
 * <p>The allow-list values are the {@code application.yml} defaults — the literals admin-service and
 * auth-service send ({@code ADMIN_SERVICE_CLIENT_ID} / {@code AUTH_SERVICE_CLIENT_ID} are never set in
 * any environment of this repository). A refused, authenticated caller → {@code 403 FORBIDDEN} and the use
 * case is never reached. No token → today's {@code 401 UNAUTHORIZED}.
 */
@WebMvcTest(TenantLifecycleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=false")
@DisplayName("TASK-BE-640 — 테넌트 라이프사이클 호출자 허용 목록 slice (bypass 꺼짐)")
class TenantLifecycleCallerAllowListSliceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T00:00:00Z");

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TenantProvisionUseCase tenantProvisionUseCase;
    @MockitoBean private JwtDecoder internalJwtDecoder;

    @BeforeEach
    void stubTokens() {
        given(internalJwtDecoder.decode("admin")).willReturn(jwt("admin", "admin-service-client"));
        given(internalJwtDecoder.decode("auth")).willReturn(jwt("auth", "auth-service-client"));
        given(internalJwtDecoder.decode("product")).willReturn(jwt("product", "product-service-client"));
        given(internalJwtDecoder.decode("security")).willReturn(jwt("security", "security-service-client"));
    }

    private static Jwt jwt(String value, String subject) {
        return Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject(subject)
                .claim("scope", "internal.invoke")
                .claim("tenant_id", "global-account-platform")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static TenantResult tenant(String id, String status) {
        return new TenantResult(id, "Acme", "B2B_ENTERPRISE", status, NOW, NOW);
    }

    private static final String CREATE = """
            {"tenantId": "acme", "displayName": "Acme", "tenantType": "B2B_ENTERPRISE"}
            """;

    // ── refused ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 product-service-client — POST · 목록 · PATCH · 단건 · HEAD 전부 403 FORBIDDEN · 유스케이스 0회")
    void productClient_everyLifecyclePath_isRefused() throws Exception {
        mockMvc.perform(post("/internal/tenants").header("Authorization", "Bearer product")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/internal/tenants").header("Authorization", "Bearer product"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(patch("/internal/tenants/acme").header("Authorization", "Bearer product")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/internal/tenants/acme").header("Authorization", "Bearer product"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(head("/internal/tenants/acme").header("Authorization", "Bearer product"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tenantProvisionUseCase);
    }

    @Test
    @DisplayName("🔴 IAM 형제라도 이름이 없으면 거절 — security-service-client PATCH → 403")
    void securityClient_patch_isRefused() throws Exception {
        mockMvc.perform(patch("/internal/tenants/acme").header("Authorization", "Bearer security")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(tenantProvisionUseCase);
    }

    @Test
    @DisplayName("🔴 auth-service-client — 쓰기 · 목록은 403 (단건 읽기만 허용)")
    void authClient_writesAndList_areRefused() throws Exception {
        mockMvc.perform(post("/internal/tenants").header("Authorization", "Bearer auth")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/tenants").header("Authorization", "Bearer auth"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/internal/tenants/acme").header("Authorization", "Bearer auth")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tenantProvisionUseCase);
    }

    // ── controls ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("대조군: admin-service-client — POST 201 · PATCH 200 · 단건 200 · 목록 200")
    void adminClient_everyLifecyclePath_passes() throws Exception {
        given(tenantProvisionUseCase.create(eq("acme"), eq("Acme"), eq("B2B_ENTERPRISE"), any()))
                .willReturn(tenant("acme", "ACTIVE"));
        given(tenantProvisionUseCase.update("acme", null, "SUSPENDED")).willReturn(tenant("acme", "SUSPENDED"));
        given(tenantProvisionUseCase.get("acme")).willReturn(tenant("acme", "ACTIVE"));
        given(tenantProvisionUseCase.list(any(), any(), anyInt(), anyInt()))
                .willReturn(new TenantPageResult(List.of(tenant("acme", "ACTIVE")), 0, 20, 1, 1));

        mockMvc.perform(post("/internal/tenants").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated());
        mockMvc.perform(patch("/internal/tenants/acme").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/internal/tenants/acme").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/internal/tenants").header("Authorization", "Bearer admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantId").value("acme"));
    }

    @Test
    @DisplayName("대조군: auth-service-client GET /internal/tenants/{id} → 200")
    void authClient_getOne_passes() throws Exception {
        given(tenantProvisionUseCase.get("acme")).willReturn(tenant("acme", "ACTIVE"));

        mockMvc.perform(get("/internal/tenants/acme").header("Authorization", "Bearer auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantType").value("B2B_ENTERPRISE"));
    }

    @Test
    @DisplayName("대조군: 토큰 없음 → 401 UNAUTHORIZED 그대로 (bypass 꺼짐에선 우회 없음)")
    void noToken_isStill401() throws Exception {
        mockMvc.perform(patch("/internal/tenants/acme")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/internal/tenants"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(tenantProvisionUseCase);
    }
}
