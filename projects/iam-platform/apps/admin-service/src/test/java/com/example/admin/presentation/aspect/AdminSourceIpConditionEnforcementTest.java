package com.example.admin.presentation.aspect;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountResult;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.presentation.AccountAdminController;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.access.SourceIpCondition;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-026 (axis ② 2단계) — verifies {@link RequiresPermissionAspect}'s 4th
 * authorization gate: the {@code SOURCE_IP} access condition. With a configured
 * allowlist ({@code 10.0.0.0/8}) an RBAC-granted admin mutation is allowed only
 * when the request source IP is in range, and otherwise 403 {@code
 * ACCESS_CONDITION_UNMET}. (The net-zero "no bean configured" path is covered by
 * {@link RequiresPermissionAspectTest}, which provides no {@code SourceIpCondition}
 * and still proceeds.)
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminSourceIpConditionEnforcementTest.Beans.class})
class AdminSourceIpConditionEnforcementTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void init() {
        jwt = new OperatorJwtTestFixture();
    }

    @TestConfiguration
    static class Beans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }

        /** Configured allowlist → the SOURCE_IP gate is active. */
        @Bean
        SourceIpCondition sourceIpCondition() {
            return SourceIpCondition.fromAllowedCidrs(List.of("10.0.0.0/8"));
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountAdminUseCase useCase;
    @MockitoBean BulkLockAccountUseCase bulkLockUseCase;
    @MockitoBean AccountServiceClient accountServiceClient;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;
    // TASK-BE-357: AccountAdminController now depends on the shared read-tenant gate.
    @MockitoBean com.example.admin.application.QueryTenantScopeGate queryTenantScopeGate;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private void grantPermission(String operatorId) {
        when(permissionEvaluator.hasPermission(operatorId, Permission.ACCOUNT_LOCK)).thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", operatorId, Instant.now(), "audit-ok"));
    }

    @Test
    @DisplayName("met: RBAC granted + in-range X-Forwarded-For ⟹ 200 (condition satisfied)")
    void allowsWhenSourceIpInRange() throws Exception {
        grantPermission("op-super");

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-ok")
                        .header("X-Operator-Reason", "compliance")
                        .header("X-Forwarded-For", "10.1.2.3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("unmet: RBAC granted + out-of-range X-Forwarded-For ⟹ 403 ACCESS_CONDITION_UNMET, mutation not executed")
    void deniesWhenSourceIpOutOfRange() throws Exception {
        grantPermission("op-super");

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-deny")
                        .header("X-Operator-Reason", "compliance")
                        .header("X-Forwarded-For", "8.8.8.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
        verify(auditor).recordDenied(
                eq(ActionCode.ACCOUNT_LOCK),
                eq(Permission.ACCOUNT_LOCK),
                eq("/api/admin/accounts/acc-1/lock"),
                eq("POST"),
                any());
    }

    @Test
    @DisplayName("fail-safe: configured + no X-Forwarded-For and out-of-range remote addr ⟹ 403")
    void deniesViaRemoteAddrFallbackWhenOutOfRange() throws Exception {
        grantPermission("op-super");

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .with(request -> { request.setRemoteAddr("203.0.113.7"); return request; })
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-remote")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("met via fallback: in-range remote addr (no X-Forwarded-For) ⟹ 200")
    void allowsViaRemoteAddrFallbackWhenInRange() throws Exception {
        grantPermission("op-super");

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .with(request -> { request.setRemoteAddr("10.0.0.9"); return request; })
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-remote-ok")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ordering: RBAC denial wins over the source-IP gate (PERMISSION_DENIED, not ACCESS_CONDITION_UNMET)")
    void permissionDenialTakesPrecedence() throws Exception {
        when(permissionEvaluator.hasPermission("op-readonly", Permission.ACCOUNT_LOCK)).thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-readonly"))
                        .header("Idempotency-Key", "idemp-perm")
                        .header("X-Operator-Reason", "compliance")
                        .header("X-Forwarded-For", "8.8.8.8") // also out-of-range
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
