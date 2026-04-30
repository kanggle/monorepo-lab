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
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies {@link RequiresPermissionAspect} denies when the evaluator reports
 * false (records a DENIED audit row) and allows when it reports true.
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        RequiresPermissionAspectTest.JwtBeans.class})
class RequiresPermissionAspectTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void init() {
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
    @MockBean AccountAdminUseCase useCase;
    @MockBean BulkLockAccountUseCase bulkLockUseCase;
    @MockBean AccountServiceClient accountServiceClient;
    @MockBean PermissionEvaluator permissionEvaluator;
    @MockBean AdminActionAuditor auditor;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    @Test
    void aspect_denies_when_evaluator_reports_false_and_records_denied_audit_row() throws Exception {
        when(permissionEvaluator.hasPermission("op-readonly", Permission.ACCOUNT_LOCK))
                .thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-readonly"))
                        .header("Idempotency-Key", "idemp-deny")
                        .header("X-Operator-Reason", "spray")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        verify(auditor).recordDenied(
                eq(ActionCode.ACCOUNT_LOCK),
                eq(Permission.ACCOUNT_LOCK),
                eq("/api/admin/accounts/acc-1/lock"),
                eq("POST"),
                any());
        verify(useCase, never()).lock(any());
    }

    @Test
    void aspect_proceeds_when_evaluator_reports_true() throws Exception {
        when(permissionEvaluator.hasPermission("op-super", Permission.ACCOUNT_LOCK))
                .thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", "op-super", Instant.now(), "audit-ok"));

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-ok")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }
}
