package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AuditQueryUseCase;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code GET /api/admin/audit?source=login_history} invoked by
 * an operator holding only {@code audit.read} writes a DENIED
 * {@code admin_actions} row with the synthesized composite permission key
 * {@code "audit.read+security.event.read"} before 403 is returned.
 */
@WebMvcTest(controllers = AuditController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AuditDeniedCrossPermissionTest.JwtBeans.class})
class AuditDeniedCrossPermissionTest {

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
    @MockBean AuditQueryUseCase useCase;
    @MockBean PermissionEvaluator permissionEvaluator;
    @MockBean AdminActionAuditor auditor;

    @BeforeEach
    void stubs() {
        // Base audit.read granted so the @RequiresPermission aspect lets the
        // controller body execute, where the cross-permission re-check runs.
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.AUDIT_READ))).thenReturn(true);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);
    }

    @Test
    void login_history_without_security_event_read_writes_composite_denied_row() throws Exception {
        String token = "Bearer " + jwt.operatorToken("op-auditor");

        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "login_history")
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        verify(auditor).recordDenied(
                eq(ActionCode.AUDIT_QUERY),
                eq("audit.read+security.event.read"),
                eq("/api/admin/audit"),
                eq("GET"),
                isNull());
    }

    @Test
    void suspicious_source_also_requires_composite_permission() throws Exception {
        String token = "Bearer " + jwt.operatorToken("op-auditor");

        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "suspicious")
                        .header("Authorization", token))
                .andExpect(status().isForbidden());

        verify(auditor).recordDenied(
                eq(ActionCode.AUDIT_QUERY),
                eq("audit.read+security.event.read"),
                eq("/api/admin/audit"),
                eq("GET"),
                isNull());
    }
}
