package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AuditQueryResult;
import com.example.admin.application.AuditQueryUseCase;
import com.example.admin.application.QueryAuditCommand;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.List;

import com.example.admin.application.exception.TenantScopeDeniedException;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AuditControllerSliceTest.JwtBeans.class})
class AuditControllerSliceTest {

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

    @MockitoBean
    AuditQueryUseCase useCase;

    @MockitoBean
    PermissionEvaluator permissionEvaluator;

    @MockitoBean
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
    void audit_query_idempotency_key_over_100_chars_returns_400_validation_error() throws Exception {
        // TASK-BE-635: bounded for guard consistency — this header is read into QueryAuditCommand but
        // never consulted by AuditQueryUseCase (a GET has no audit-row key to overflow).
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "k".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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

    /**
     * TASK-BE-658 — the contract's value is {@code admin} ({@code admin-api.md} § GET /api/admin/audit). This test used
     * to send {@code admin_actions}, which no source matches: the use case read nothing and answered an empty 200, so
     * the test was green for a reason unrelated to its name. The captured command pins what reached the use case.
     */
    @Test
    void audit_query_admin_source_only_requires_audit_read() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .param("source", "admin")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryAuditCommand> cmd = ArgumentCaptor.forClass(QueryAuditCommand.class);
        verify(useCase).query(cmd.capture());
        assertThat(cmd.getValue().source()).isEqualTo("admin");
    }

    /**
     * TASK-BE-655 — no source + accountId + no security.event.read: the use-case would have mixed in the
     * security-service sources (it does for source=null). The controller narrows to the admin source instead;
     * AuditQueryUseCaseTest#query_sourceAdmin_securityClientNotCalled pins that "admin" never calls
     * security-service. 200, not 403 — and no DENIED row, nothing was asked for that was refused.
     */
    @Test
    void audit_query_no_source_with_account_without_security_event_read_narrows_to_admin() throws Exception {
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);

        mockMvc.perform(get("/api/admin/audit")
                        .param("accountId", "acc-1")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryAuditCommand> cmd = ArgumentCaptor.forClass(QueryAuditCommand.class);
        verify(useCase).query(cmd.capture());
        assertThat(cmd.getValue().source()).isEqualTo("admin");
        assertThat(cmd.getValue().accountId()).isEqualTo("acc-1");
        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    /** TASK-BE-655 control group: holding both permissions, the same query still asks for every source. */
    @Test
    void audit_query_no_source_with_account_with_both_permissions_keeps_all_sources() throws Exception {
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);

        mockMvc.perform(get("/api/admin/audit")
                        .param("accountId", "acc-1")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryAuditCommand> cmd = ArgumentCaptor.forClass(QueryAuditCommand.class);
        verify(useCase).query(cmd.capture());
        assertThat(cmd.getValue().source()).isNull();
    }

    /** TASK-BE-655: with no accountId no security source is read anyway — the overview call is untouched. */
    @Test
    void audit_query_no_source_no_account_is_unchanged() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<QueryAuditCommand> cmd = ArgumentCaptor.forClass(QueryAuditCommand.class);
        verify(useCase).query(cmd.capture());
        assertThat(cmd.getValue().source()).isNull();
        verify(permissionEvaluator, never()).hasAllPermissions(anyString(), any(Collection.class));
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
