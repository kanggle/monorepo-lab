package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ManageOperatorOrgScopeUseCase;
import com.example.admin.application.exception.AssignmentNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeMismatchException;
import com.example.admin.application.port.OperatorTenantAssignmentPort.AssignmentView;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-339 — slice test for {@link OperatorOrgScopeController}: reason gate,
 * authz, error-code mapping (TENANT_SCOPE_MISMATCH / ASSIGNMENT_NOT_FOUND /
 * OPERATOR_NOT_FOUND / REASON_REQUIRED / PERMISSION_DENIED), and the
 * {@code @JsonInclude(NON_NULL)} omission of a null orgScope.
 */
@WebMvcTest(controllers = OperatorOrgScopeController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        OperatorOrgScopeControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class OperatorOrgScopeControllerSliceTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManageOperatorOrgScopeUseCase useCase;

    // TASK-BE-347 — the controller now also depends on the assign/unassign use-case.
    @MockitoBean
    com.example.admin.application.ManageOperatorAssignmentUseCase assignmentUseCase;

    @MockitoBean
    PermissionEvaluator permissionEvaluator;

    @MockitoBean
    AdminActionAuditor auditor;

    @BeforeEach
    void grantAll() {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-actor");
    }

    // ───────────────────────── GET /assignments ─────────────────────────

    @Test
    void list_assignments_returns_active_tenant_row() throws Exception {
        when(useCase.listAssignments("op-target", "acme-corp"))
                .thenReturn(List.of(new AssignmentView("acme-corp", List.of("dept-sales"), null)));

        mockMvc.perform(get("/api/admin/operators/op-target/assignments")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.assignments[0].orgScope[0]").value("dept-sales"));
    }

    @Test
    void list_assignments_omits_orgScope_when_null() throws Exception {
        when(useCase.listAssignments("op-target", "acme-corp"))
                .thenReturn(List.of(new AssignmentView("acme-corp", null, null)));

        mockMvc.perform(get("/api/admin/operators/op-target/assignments")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.assignments[0].orgScope").doesNotExist());
    }

    @Test
    void list_assignments_empty_when_not_assigned() throws Exception {
        when(useCase.listAssignments("op-target", "globex")).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/operators/op-target/assignments")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "globex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments").isEmpty());
    }

    @Test
    void list_assignments_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/admin/operators/op-target/assignments")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ───────────────────────── PUT .../org-scope ─────────────────────────

    @Test
    void set_org_scope_returns_200() throws Exception {
        when(useCase.setOrgScope(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new AssignmentView("acme-corp", List.of("dept-sales"), null));

        mockMvc.perform(put("/api/admin/operators/op-target/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.orgScope[0]").value("dept-sales"));
    }

    @Test
    void set_org_scope_clear_null_omits_orgScope_in_response() throws Exception {
        when(useCase.setOrgScope(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new AssignmentView("acme-corp", null, null));

        mockMvc.perform(put("/api/admin/operators/op-target/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.orgScope").doesNotExist());
    }

    @Test
    void set_org_scope_missing_reason_returns_400() throws Exception {
        mockMvc.perform(put("/api/admin/operators/op-target/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void set_org_scope_tenant_mismatch_returns_403() throws Exception {
        doThrow(new TenantScopeMismatchException("mismatch"))
                .when(useCase).setOrgScope(anyString(), anyString(), anyString(), any(), any(), anyString());

        mockMvc.perform(put("/api/admin/operators/op-target/assignments/globex/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_MISMATCH"));
    }

    @Test
    void set_org_scope_assignment_not_found_returns_404() throws Exception {
        doThrow(new AssignmentNotFoundException("no row"))
                .when(useCase).setOrgScope(anyString(), anyString(), anyString(), any(), any(), anyString());

        mockMvc.perform(put("/api/admin/operators/op-target/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void set_org_scope_operator_not_found_returns_404() throws Exception {
        doThrow(new OperatorNotFoundException("no operator"))
                .when(useCase).setOrgScope(anyString(), anyString(), anyString(), any(), any(), anyString());

        mockMvc.perform(put("/api/admin/operators/ghost/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_NOT_FOUND"));
    }

    @Test
    void set_org_scope_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);

        mockMvc.perform(put("/api/admin/operators/op-target/assignments/acme-corp/org-scope")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgScope": ["dept-sales"]}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
