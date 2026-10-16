package com.example.account.presentation;

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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-640 AC-3 — a blank allow-list fails CLOSED: with both keys set to the empty string, even the
 * two named callers are refused ({@code 403 FORBIDDEN}). A mis-configuration must surface as a refusal,
 * never reopen the API to every {@code internal.invoke} holder.
 *
 * <p>The «key absent» half (no property at all) is measured by the policy's unit test, because
 * {@code application.yml} always supplies the key in a Spring context.
 */
@WebMvcTest(TenantLifecycleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "internal.api.bypass-when-unconfigured=false",
        "internal.api.tenant-lifecycle.write-callers=",
        "internal.api.tenant-lifecycle.read-callers="
})
@DisplayName("TASK-BE-640 AC-3 — 빈 허용 목록은 닫히는 쪽으로 실패한다")
class TenantLifecycleCallerAllowListFailClosedSliceTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private TenantProvisionUseCase tenantProvisionUseCase;
    @MockitoBean private JwtDecoder internalJwtDecoder;

    @BeforeEach
    void stubTokens() {
        given(internalJwtDecoder.decode("admin")).willReturn(jwt("admin", "admin-service-client"));
        given(internalJwtDecoder.decode("auth")).willReturn(jwt("auth", "auth-service-client"));
    }

    private static Jwt jwt(String value, String subject) {
        return Jwt.withTokenValue(value)
                .header("alg", "RS256")
                .subject(subject)
                .claim("scope", "internal.invoke")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("🔴 빈 write-callers — admin-service-client 의 POST · 목록 · PATCH 도 403 FORBIDDEN")
    void blankWriteCallers_refuseAdmin() throws Exception {
        mockMvc.perform(post("/internal/tenants").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\": \"acme\", \"displayName\": \"Acme\", \"tenantType\": \"B2B_ENTERPRISE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/internal/tenants").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/internal/tenants/acme").header("Authorization", "Bearer admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tenantProvisionUseCase);
    }

    @Test
    @DisplayName("🔴 빈 read-callers — admin · auth 의 단건 읽기도 403 FORBIDDEN")
    void blankReadCallers_refuseAdminAndAuth() throws Exception {
        mockMvc.perform(get("/internal/tenants/acme").header("Authorization", "Bearer admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/internal/tenants/acme").header("Authorization", "Bearer auth"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(tenantProvisionUseCase);
    }
}
