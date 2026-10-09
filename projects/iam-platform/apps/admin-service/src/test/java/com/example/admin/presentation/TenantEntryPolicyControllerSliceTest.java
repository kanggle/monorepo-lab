package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.TenantEntryPolicyPrecheckUseCase;
import com.example.admin.application.TenantEntryPolicyUseCase;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.TenantEntryPolicyManagementPort.EntryPolicyView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S5 — the HTTP shape of {@code GET|PUT /api/admin/tenants/{tenantId}/entry-policy}
 * (admin-api.md § Tenant Entry Policy): the {@code tenant.security.manage} gate, the body rule
 * ({@code requireMfa} a JSON boolean), the reason header, and the error-code mapping.
 */
@WebMvcTest(controllers = TenantEntryPolicyController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        TenantEntryPolicyControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class TenantEntryPolicyControllerSliceTest {

    private static OperatorJwtTestFixture jwt;
    private static final String HOLDER_ID = "op-tenant-admin";
    private static final String SUPPORT_ID = "op-support-readonly";

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean TenantEntryPolicyUseCase useCase;
    @MockitoBean TenantEntryPolicyPrecheckUseCase precheckUseCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor adminActionAuditor; // required by RequiresPermissionAspect

    @BeforeEach
    void permissions() {
        lenient().when(permissionEvaluator.hasPermission(HOLDER_ID, Permission.TENANT_SECURITY_MANAGE)).thenReturn(true);
        lenient().when(permissionEvaluator.hasPermission(SUPPORT_ID, Permission.TENANT_SECURITY_MANAGE)).thenReturn(false);
    }

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    @Test
    @DisplayName("🔴 no tenant.security.manage → 403 PERMISSION_DENIED (GET and PUT); the use-case is never reached")
    void withoutPermission_403() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/acme/entry-policy").header("Authorization", bearer(SUPPORT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(put("/api/admin/tenants/acme/entry-policy")
                        .header("Authorization", bearer(SUPPORT_ID))
                        .header("X-Operator-Reason", "on")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireMfa\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(useCase, never()).get(any(), anyString());
        verify(useCase, never()).set(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("GET never set → 200 with every key present (requireMfa=false, updatedAt/updatedBy null)")
    void get_off_allKeysPresent() throws Exception {
        when(useCase.get(any(), eq("acme"))).thenReturn(new EntryPolicyView("acme", false, null, null));

        mockMvc.perform(get("/api/admin/tenants/acme/entry-policy").header("Authorization", bearer(HOLDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.requireMfa").value(false))
                .andExpect(jsonPath("$.updatedAt").isEmpty())
                .andExpect(jsonPath("$.updatedBy").isEmpty());
    }

    @Test
    @DisplayName("PUT {requireMfa:true} + reason → 200 with the written state; reason is percent-decoded")
    void put_on() throws Exception {
        when(useCase.set(any(), eq("acme"), eq(true), eq("회사 정책")))
                .thenReturn(new EntryPolicyView("acme", true, Instant.parse("2026-10-09T01:02:03Z"), "op-uuid"));

        mockMvc.perform(put("/api/admin/tenants/acme/entry-policy")
                        .header("Authorization", bearer(HOLDER_ID))
                        .header("X-Operator-Reason", "%ED%9A%8C%EC%82%AC%20%EC%A0%95%EC%B1%85")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireMfa\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireMfa").value(true))
                .andExpect(jsonPath("$.updatedBy").value("op-uuid"))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-09T01:02:03Z"));
    }

    @Test
    @DisplayName("PUT without X-Operator-Reason → 400 REASON_REQUIRED, nothing written")
    void put_missingReason_400() throws Exception {
        mockMvc.perform(put("/api/admin/tenants/acme/entry-policy")
                        .header("Authorization", bearer(HOLDER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireMfa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        verify(useCase, never()).set(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("PUT {requireMfa:\"true\"} / {} → 400 VALIDATION_ERROR (a string or a missing key is not a decision)")
    void put_nonBoolean_400() throws Exception {
        for (String body : new String[] {"{\"requireMfa\":\"true\"}", "{}", "{\"requireMfa\":1}", "{\"requireMfa\":null}"}) {
            mockMvc.perform(put("/api/admin/tenants/acme/entry-policy")
                            .header("Authorization", bearer(HOLDER_ID))
                            .header("X-Operator-Reason", "r")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verify(useCase, never()).set(any(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("use-case scope refusal → 403 TENANT_SCOPE_DENIED")
    void put_scopeDenied_403() throws Exception {
        when(useCase.set(any(), eq("other"), eq(true), any()))
                .thenThrow(new TenantScopeDeniedException("not scoped"));
        mockMvc.perform(put("/api/admin/tenants/other/entry-policy")
                        .header("Authorization", bearer(HOLDER_ID))
                        .header("X-Operator-Reason", "r")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireMfa\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("'*' (use-case IllegalArgumentException) → 400 VALIDATION_ERROR")
    void put_platformSentinel_400() throws Exception {
        when(useCase.set(any(), eq("*"), eq(true), any()))
                .thenThrow(new IllegalArgumentException("tenantId '*' cannot hold an entry policy"));
        mockMvc.perform(put("/api/admin/tenants/*/entry-policy")
                        .header("Authorization", bearer(HOLDER_ID))
                        .header("X-Operator-Reason", "r")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireMfa\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("unknown tenant → 404 TENANT_NOT_FOUND · authority down → 503 DOWNSTREAM_ERROR · race → 409")
    void put_errorMapping() throws Exception {
        when(useCase.set(any(), eq("ghost"), eq(true), any())).thenThrow(new TenantNotFoundException("ghost"));
        when(useCase.set(any(), eq("down"), eq(true), any())).thenThrow(new DownstreamFailureException("x", null));
        when(useCase.set(any(), eq("race"), eq(true), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Object.class, "race"));

        String[][] cases = {{"ghost", "404", "TENANT_NOT_FOUND"}, {"down", "503", "DOWNSTREAM_ERROR"},
                {"race", "409", "OPTIMISTIC_LOCK_CONFLICT"}};
        for (String[] c : cases) {
            mockMvc.perform(put("/api/admin/tenants/" + c[0] + "/entry-policy")
                            .header("Authorization", bearer(HOLDER_ID))
                            .header("X-Operator-Reason", "r")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requireMfa\":true}"))
                    .andExpect(status().is(Integer.parseInt(c[1])))
                    .andExpect(jsonPath("$.code").value(c[2]));
        }
    }

    // ---- enrolment-summary (the «켜기 전 사전 점검» read) ----------------------------------------

    @Test
    @DisplayName("GET enrolment-summary → 200 {operators, enrolled, notEnrolled, unlinked}")
    void summary_ok() throws Exception {
        when(precheckUseCase.summarize(any(), eq("acme")))
                .thenReturn(new TenantEntryPolicyPrecheckUseCase.EnrolmentSummary("acme", 5, 2, 2, 1));
        mockMvc.perform(get("/api/admin/tenants/acme/entry-policy/enrolment-summary")
                        .header("Authorization", bearer(HOLDER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.operators").value(5))
                .andExpect(jsonPath("$.enrolled").value(2))
                .andExpect(jsonPath("$.notEnrolled").value(2))
                .andExpect(jsonPath("$.unlinked").value(1));
    }

    @Test
    @DisplayName("enrolment-summary: no permission → 403 · auth-service down → 503 (never a made-up number)")
    void summary_errors() throws Exception {
        mockMvc.perform(get("/api/admin/tenants/acme/entry-policy/enrolment-summary")
                        .header("Authorization", bearer(SUPPORT_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(precheckUseCase, never()).summarize(any(), anyString());

        when(precheckUseCase.summarize(any(), eq("acme"))).thenThrow(new DownstreamFailureException("auth down", null));
        mockMvc.perform(get("/api/admin/tenants/acme/entry-policy/enrolment-summary")
                        .header("Authorization", bearer(HOLDER_ID)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_ERROR"));
    }
}
