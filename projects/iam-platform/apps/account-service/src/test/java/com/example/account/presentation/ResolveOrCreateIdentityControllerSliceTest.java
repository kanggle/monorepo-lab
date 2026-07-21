package com.example.account.presentation;

import com.example.account.application.service.ResolveOrCreateIdentityUseCase;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase.Outcome;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase.ResolveOrCreateIdentityResult;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.identity.ResolveOrCreateIdentityController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-374 (ADR-MONO-034 step 3d): slice tests for the resolve-or-create
 * identity EP. Verifies the colon-verb routing, the three outcome shapes, the
 * tenant-scope defense-in-depth, and email validation.
 */
@WebMvcTest(ResolveOrCreateIdentityController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("ResolveOrCreateIdentityController slice — TASK-BE-374")
class ResolveOrCreateIdentityControllerSliceTest {

    private static final String TENANT_ID = "wms";
    private static final String PATH = "/internal/tenants/{tenantId}/identities:resolveOrCreate";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ResolveOrCreateIdentityUseCase resolveOrCreateIdentityUseCase;

    @Test
    @DisplayName("미존재 → CREATED + identityId")
    void created_returns200() throws Exception {
        given(resolveOrCreateIdentityUseCase.execute(eq(TENANT_ID), eq("new@example.com"), eq(false)))
                .willReturn(new ResolveOrCreateIdentityResult("idy-new", Outcome.CREATED));

        mockMvc.perform(post(PATH, TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "new@example.com", "reuseExisting": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value("idy-new"))
                .andExpect(jsonPath("$.outcome").value("CREATED"));
    }

    @Test
    @DisplayName("존재 + reuseExisting=true → REUSED + 기존 id")
    void reused_returns200() throws Exception {
        given(resolveOrCreateIdentityUseCase.execute(eq(TENANT_ID), eq("dup@example.com"), eq(true)))
                .willReturn(new ResolveOrCreateIdentityResult("idy-existing", Outcome.REUSED));

        mockMvc.perform(post(PATH, TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "dup@example.com", "reuseExisting": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value("idy-existing"))
                .andExpect(jsonPath("$.outcome").value("REUSED"));
    }

    @Test
    @DisplayName("존재 + reuseExisting 누락(=false) → EXISTS_NOT_REUSED + identityId null (no merge)")
    void existsNotReused_nullId_returns200() throws Exception {
        given(resolveOrCreateIdentityUseCase.execute(eq(TENANT_ID), eq("dup@example.com"), eq(false)))
                .willReturn(new ResolveOrCreateIdentityResult(null, Outcome.EXISTS_NOT_REUSED));

        mockMvc.perform(post(PATH, TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "dup@example.com" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").doesNotExist())
                .andExpect(jsonPath("$.outcome").value("EXISTS_NOT_REUSED"));
    }

    @Test
    @DisplayName("email 누락 → 400 VALIDATION_ERROR")
    void missingEmail_returns400() throws Exception {
        mockMvc.perform(post(PATH, TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reuseExisting": true }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("tenant scope mismatch → 403 TENANT_SCOPE_DENIED")
    void tenantScopeMismatch_returns403() throws Exception {
        mockMvc.perform(post(PATH, TENANT_ID)
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "x@example.com", "reuseExisting": true }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }
}
