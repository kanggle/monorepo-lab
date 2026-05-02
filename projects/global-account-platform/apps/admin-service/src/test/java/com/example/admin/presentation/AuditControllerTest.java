package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AuditQueryResult;
import com.example.admin.application.AuditQueryUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.List;

import com.example.admin.application.exception.TenantScopeDeniedException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AuditControllerTest.JwtBeans.class})
class AuditControllerTest {

    private static OperatorJwtTestFixture jwt;

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

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditQueryUseCase useCase;

    @MockBean
    PermissionEvaluator permissionEvaluator;

    @MockBean
    AdminActionAuditor auditor;

    @BeforeEach
    void defaults() {
        when(useCase.query(any())).thenReturn(new AuditQueryResult(List.of(), 0, 20, 0L, 0));
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.AUDIT_READ))).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-1");
    }

    @Test
    void audit_query_with_audit_read_permission_returns_200() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void audit_query_without_jwt_returns_401_token_invalid() throws Exception {
        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void audit_query_login_history_without_security_event_read_returns_403() throws Exception {
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);

        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "login_history")
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void audit_query_login_history_with_both_permissions_returns_200() throws Exception {
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);

        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "login_history")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void audit_query_admin_source_only_requires_audit_read() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "admin_actions")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void audit_query_cross_tenant_denied_returns_403_tenant_scope_denied() throws Exception {
        // TASK-BE-249: use-case throws TenantScopeDeniedException when normal operator requests different tenant
        doThrow(new TenantScopeDeniedException("cross-tenant access denied"))
                .when(useCase).query(any());

        mockMvc.perform(get("/api/admin/audit")
                        .param("tenantId", "other-platform")
                        .header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }
}
