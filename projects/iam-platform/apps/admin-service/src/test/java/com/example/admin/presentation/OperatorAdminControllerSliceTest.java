package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ChangeMyPasswordUseCase;
import com.example.admin.application.CreateOperatorUseCase;
import com.example.admin.application.OperatorQueryService;
import com.example.admin.application.PatchOperatorRoleUseCase;
import com.example.admin.application.PatchOperatorStatusUseCase;
import com.example.admin.application.UpdateOwnOperatorProfileUseCase;
import com.example.admin.application.exception.CurrentPasswordMismatchException;
import com.example.admin.application.exception.OperatorAccountNotFoundException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.PasswordPolicyViolationException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.SelfSuspendForbiddenException;
import com.example.admin.application.exception.StateTransitionInvalidException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OperatorAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        OperatorAdminControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class OperatorAdminControllerSliceTest {

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

    @MockitoBean OperatorQueryService queryService;
    @MockitoBean CreateOperatorUseCase createOperatorUseCase;
    @MockitoBean PatchOperatorRoleUseCase patchOperatorRoleUseCase;
    @MockitoBean PatchOperatorStatusUseCase patchOperatorStatusUseCase;
    @MockitoBean ChangeMyPasswordUseCase changeMyPasswordUseCase;
    @MockitoBean UpdateOwnOperatorProfileUseCase updateOwnOperatorProfileUseCase;
    @MockitoBean com.example.admin.application.UpdateOperatorProfileUseCase updateOperatorProfileUseCase;
    // TASK-BE-373 (ADR-MONO-034 step 3c): operator↔identity link/unlink use cases
    // wired into OperatorAdminController — mocked so the WebMvc slice context loads.
    @MockitoBean com.example.admin.application.LinkOperatorIdentityUseCase linkOperatorIdentityUseCase;
    @MockitoBean com.example.admin.application.UnlinkOperatorIdentityUseCase unlinkOperatorIdentityUseCase;
    // TASK-BE-388 (ADR-MONO-024 D3 read mirror): grantable-roles read hint collaborators
    // wired into OperatorAdminController — mocked so the WebMvc slice context loads.
    @MockitoBean com.example.admin.application.port.AdminOperatorPort adminOperatorPort;
    @MockitoBean com.example.admin.application.RoleGrantGuard roleGrantGuard;

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

    // -------------------------------------------------------- GET /me

    @Test
    void me_returns_current_operator_without_permission_annotation() throws Exception {
        when(queryService.getCurrentOperator("op-actor")).thenReturn(
                new OperatorQueryService.OperatorSummary(
                        "op-actor", "actor@example.com", "Actor",
                        "ACTIVE", List.of("SUPER_ADMIN"), true,
                        Instant.parse("2026-04-24T10:00:00Z"),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null));

        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value("op-actor"))
                .andExpect(jsonPath("$.email").value("actor@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.totpEnrolled").value(true));
    }

    @Test
    void me_without_jwt_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void me_missing_operator_row_returns_401_token_invalid() throws Exception {
        doThrow(new OperatorUnauthorizedException("operator not found"))
                .when(queryService).getCurrentOperator("op-actor");

        mockMvc.perform(get("/api/admin/me").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // -------------------------------- GET /operators/grantable-roles (BE-388)

    @Test
    void grantable_roles_returns_guard_projection() throws Exception {
        when(adminOperatorPort.findAllRoles()).thenReturn(List.of(
                new com.example.admin.application.port.AdminOperatorPort.RoleView(1L, "SUPER_ADMIN", "", false),
                new com.example.admin.application.port.AdminOperatorPort.RoleView(2L, "TENANT_ADMIN", "", false)));
        when(roleGrantGuard.grantableRoleNames(any(), any())).thenReturn(List.of("TENANT_ADMIN"));

        mockMvc.perform(get("/api/admin/operators/grantable-roles").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TENANT_ADMIN"))
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void grantable_roles_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/admin/operators/grantable-roles").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ----------------------------------------------- GET /operators

    @Test
    void list_operators_returns_page() throws Exception {
        OperatorQueryService.OperatorSummary s = new OperatorQueryService.OperatorSummary(
                "op-1", "one@example.com", "One", "ACTIVE", List.of("SUPPORT_LOCK"),
                false, null, Instant.parse("2026-01-01T00:00:00Z"), null);
        when(queryService.listOperators(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new OperatorQueryService.OperatorPage(List.of(s), 1L, 0, 20, 1));

        mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operatorId").value("op-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void list_operators_emits_operatorContext_when_finance_default_account_id_present() throws Exception {
        // TASK-BE-308: operator with non-null financeDefaultAccountId → response item
        // carries operatorContext.defaultAccountId.
        OperatorQueryService.OperatorSummary s = new OperatorQueryService.OperatorSummary(
                "op-1", "one@example.com", "One", "ACTIVE", List.of("SUPPORT_LOCK"),
                false, null, Instant.parse("2026-01-01T00:00:00Z"),
                "01928c4a-7e9f-7c00-9a40-d2b1f5e8a000");
        when(queryService.listOperators(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new OperatorQueryService.OperatorPage(List.of(s), 1L, 0, 20, 1));

        mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operatorId").value("op-1"))
                .andExpect(jsonPath("$.content[0].operatorContext.defaultAccountId")
                        .value("01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"));
    }

    @Test
    void list_operators_omits_operatorContext_when_finance_default_account_id_null() throws Exception {
        // TASK-BE-308 AC-6: NULL column → @JsonInclude(Include.NON_NULL) omits the field.
        // The whole "operatorContext" substring must NOT appear in the response item.
        OperatorQueryService.OperatorSummary s = new OperatorQueryService.OperatorSummary(
                "op-2", "two@example.com", "Two", "ACTIVE", List.of("SUPPORT_LOCK"),
                false, null, Instant.parse("2026-01-01T00:00:00Z"), null);
        when(queryService.listOperators(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new OperatorQueryService.OperatorPage(List.of(s), 1L, 0, 20, 1));

        String body = mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operatorId").value("op-2"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("operatorContext");
    }

    @Test
    void list_operators_omits_operatorContext_when_finance_default_account_id_blank() throws Exception {
        // TASK-BE-308: defensive — legacy DB state with whitespace-only is treated
        // as absent (mirrors BE-304 registry surface).
        OperatorQueryService.OperatorSummary s = new OperatorQueryService.OperatorSummary(
                "op-3", "three@example.com", "Three", "ACTIVE", List.of("SUPPORT_LOCK"),
                false, null, Instant.parse("2026-01-01T00:00:00Z"), "   ");
        when(queryService.listOperators(any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new OperatorQueryService.OperatorPage(List.of(s), 1L, 0, 20, 1));

        String body = mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("operatorContext");
    }

    @Test
    void list_operators_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void list_operators_size_too_large_returns_400() throws Exception {
        mockMvc.perform(get("/api/admin/operators?size=500").header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------------------------------------------- POST /operators

    @Test
    void create_operator_returns_201() throws Exception {
        // TASK-BE-249: use 7-arg method signature
        when(createOperatorUseCase.createOperator(
                anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), any()))
                .thenReturn(new CreateOperatorUseCase.CreateOperatorResult(
                        "op-new", "new@example.com", "New", "ACTIVE",
                        List.of("SUPPORT_LOCK"), false,
                        Instant.parse("2026-04-24T10:00:00Z"),
                        "audit-new", "fan-platform"));

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "displayName": "New",
                                  "password": "StrongPass1!",
                                  "roles": ["SUPPORT_LOCK"],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value("op-new"))
                .andExpect(jsonPath("$.auditId").value("audit-new"))
                .andExpect(jsonPath("$.tenantId").value("fan-platform"));
    }

    @Test
    void create_operator_duplicate_email_returns_409() throws Exception {
        doThrow(new OperatorEmailConflictException("dup"))
                .when(createOperatorUseCase).createOperator(
                        anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dup@example.com",
                                  "displayName": "Dup",
                                  "password": "StrongPass1!",
                                  "roles": [],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_EMAIL_CONFLICT"));
    }

    // TASK-MONO-334 (ADR-MONO-035 amendment) — the target email has no signed-up
    // account in the tenant → 422 OPERATOR_ACCOUNT_NOT_FOUND (distinct from the 409
    // email-conflict above).
    @Test
    void create_operator_account_not_found_returns_422() throws Exception {
        doThrow(new OperatorAccountNotFoundException("No signed-up account exists for this email in the target tenant"))
                .when(createOperatorUseCase).createOperator(
                        anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-2b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ghost@example.com",
                                  "displayName": "Ghost",
                                  "password": "StrongPass1!",
                                  "roles": [],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("OPERATOR_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void create_operator_unknown_role_returns_400() throws Exception {
        doThrow(new RoleNotFoundException("GHOST"))
                .when(createOperatorUseCase).createOperator(
                        anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "displayName": "X",
                                  "password": "StrongPass1!",
                                  "roles": ["GHOST"],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
    }

    @Test
    void create_operator_weak_password_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "displayName": "X",
                                  "password": "short",
                                  "roles": [],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_operator_missing_tenant_id_returns_400() throws Exception {
        // TASK-BE-249: tenantId is now required
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-5a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "displayName": "X",
                                  "password": "StrongPass1!",
                                  "roles": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_operator_tenant_scope_denied_returns_403() throws Exception {
        // TASK-BE-249: non-platform-scope actor trying to create tenantId='*' → 403
        doThrow(new com.example.admin.application.exception.TenantScopeDeniedException("platform-scope only"))
                .when(createOperatorUseCase).createOperator(
                        anyString(), anyString(), anyString(), any(), any(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "provisioning")
                        .header("Idempotency-Key", "idemp-ts-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "super@example.com",
                                  "displayName": "Super",
                                  "password": "StrongPass1!",
                                  "roles": ["SUPER_ADMIN"],
                                  "tenantId": "*"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    void create_operator_missing_reason_header_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/operators")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "displayName": "X",
                                  "password": "StrongPass1!",
                                  "roles": [],
                                  "tenantId": "fan-platform"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    // ---------------------------------- PATCH /operators/{id}/roles

    @Test
    void patch_roles_returns_200() throws Exception {
        when(patchOperatorRoleUseCase.patchRoles(anyString(), any(), any(), anyString()))
                .thenReturn(new PatchOperatorRoleUseCase.PatchRolesResult(
                        "op-target",
                        List.of("SUPPORT_READONLY"),
                        "audit-patch"));

        mockMvc.perform(patch("/api/admin/operators/op-target/roles")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": ["SUPPORT_READONLY"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value("op-target"))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_READONLY"))
                .andExpect(jsonPath("$.auditId").value("audit-patch"));
    }

    @Test
    void patch_roles_missing_operator_returns_404() throws Exception {
        doThrow(new OperatorNotFoundException("missing"))
                .when(patchOperatorRoleUseCase).patchRoles(anyString(), any(), any(), anyString());

        mockMvc.perform(patch("/api/admin/operators/ghost/roles")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "rotation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roles": []}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_NOT_FOUND"));
    }

    // ---------------------------------- PATCH /operators/{id}/status

    @Test
    void patch_status_success_returns_200() throws Exception {
        when(patchOperatorStatusUseCase.patchStatus(anyString(), anyString(), any(), anyString()))
                .thenReturn(new PatchOperatorStatusUseCase.PatchStatusResult(
                        "op-target", "ACTIVE", "SUSPENDED", "audit-sus"));

        mockMvc.perform(patch("/api/admin/operators/op-target/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "violation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value("op-target"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("SUSPENDED"));
    }

    @Test
    void patch_status_self_suspend_returns_400() throws Exception {
        doThrow(new SelfSuspendForbiddenException("self"))
                .when(patchOperatorStatusUseCase).patchStatus(anyString(), anyString(), any(), anyString());

        mockMvc.perform(patch("/api/admin/operators/op-target/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "self")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "SUSPENDED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_SUSPEND_FORBIDDEN"));
    }

    @Test
    void patch_status_same_value_returns_400_state_transition() throws Exception {
        doThrow(new StateTransitionInvalidException("already"))
                .when(patchOperatorStatusUseCase).patchStatus(anyString(), anyString(), any(), anyString());

        mockMvc.perform(patch("/api/admin/operators/op-target/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "noop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    void patch_status_invalid_enum_returns_400_validation_error() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/op-target/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DELETED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // -------------------------------------- PATCH /operators/me/password

    @Test
    void change_my_password_success_returns_204() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "OldPass1!", "newPassword": "NewPass2@"}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void change_my_password_without_jwt_returns_401() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "OldPass1!", "newPassword": "NewPass2@"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void change_my_password_current_mismatch_returns_400() throws Exception {
        doThrow(new CurrentPasswordMismatchException())
                .when(changeMyPasswordUseCase).changeMyPassword(anyString(), anyString(), anyString());

        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Wrong1!", "newPassword": "NewPass2@"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));
    }

    @Test
    void change_my_password_policy_violation_returns_400() throws Exception {
        doThrow(new PasswordPolicyViolationException("Password must contain at least 3 categories"))
                .when(changeMyPasswordUseCase).changeMyPassword(anyString(), anyString(), anyString());

        mockMvc.perform(patch("/api/admin/operators/me/password")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "OldPass1!", "newPassword": "weakpass"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    // -------------------------------------- PATCH /operators/me/profile (TASK-BE-306)

    @Test
    void update_my_profile_set_uuid_returns_204() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void update_my_profile_clear_with_null_returns_204() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":null}}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void update_my_profile_without_jwt_returns_401() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"x"}}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void update_my_profile_whitespace_only_returns_400_invalid_request() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"   "}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void update_my_profile_empty_body_returns_400_invalid_request() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void update_my_profile_empty_operator_context_returns_400_invalid_request() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void update_my_profile_unknown_nested_key_returns_400_invalid_request() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"wmsDefaultWarehouseId":"x"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void update_my_profile_over_36_chars_returns_400_invalid_request() throws Exception {
        // 37-character value (one over the column length cap)
        String tooLong = "a".repeat(37);
        mockMvc.perform(patch("/api/admin/operators/me/profile")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"%s"}}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // -------------------------------------- PATCH /operators/{operatorId}/profile (TASK-BE-307)

    @Test
    void update_operator_profile_admin_returns_204() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/op-target/profile")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "onboarding bulk-provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isNoContent());
    }

    @Test
    void update_operator_profile_admin_missing_reason_returns_400_reason_required() throws Exception {
        mockMvc.perform(patch("/api/admin/operators/op-target/profile")
                        .header("Authorization", bearer())
                        // NO X-Operator-Reason
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void update_operator_profile_admin_without_permission_returns_403() throws Exception {
        // RequiresPermissionAspect gates via permissionEvaluator; deny it.
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);

        mockMvc.perform(patch("/api/admin/operators/op-target/profile")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "any reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void update_operator_profile_admin_target_not_found_returns_404() throws Exception {
        org.mockito.Mockito.doThrow(new com.example.admin.application.exception.OperatorNotFoundException(
                        "Operator not found for operatorId=op-missing"))
                .when(updateOperatorProfileUseCase)
                .update(org.mockito.ArgumentMatchers.eq("op-missing"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(patch("/api/admin/operators/op-missing/profile")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "any reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_NOT_FOUND"));
    }

    @Test
    void update_operator_profile_admin_self_via_admin_path_returns_400() throws Exception {
        org.mockito.Mockito.doThrow(new com.example.admin.application.exception.SelfProfileUpdateForbiddenException(
                        "Self profile updates must go through /api/admin/operators/me/profile"))
                .when(updateOperatorProfileUseCase)
                .update(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());

        mockMvc.perform(patch("/api/admin/operators/op-actor/profile")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "any reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorContext":{"defaultAccountId":"01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH"));
    }
}
