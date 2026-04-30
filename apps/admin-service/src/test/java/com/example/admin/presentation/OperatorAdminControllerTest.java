package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ChangeMyPasswordUseCase;
import com.example.admin.application.CreateOperatorUseCase;
import com.example.admin.application.OperatorQueryService;
import com.example.admin.application.PatchOperatorRoleUseCase;
import com.example.admin.application.PatchOperatorStatusUseCase;
import com.example.admin.application.exception.CurrentPasswordMismatchException;
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
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

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
        OperatorAdminControllerTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class OperatorAdminControllerTest {

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

    @MockBean OperatorQueryService queryService;
    @MockBean CreateOperatorUseCase createOperatorUseCase;
    @MockBean PatchOperatorRoleUseCase patchOperatorRoleUseCase;
    @MockBean PatchOperatorStatusUseCase patchOperatorStatusUseCase;
    @MockBean ChangeMyPasswordUseCase changeMyPasswordUseCase;

    @MockBean
    PermissionEvaluator permissionEvaluator;

    @MockBean
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
                        Instant.parse("2026-01-01T00:00:00Z")));

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

    // ----------------------------------------------- GET /operators

    @Test
    void list_operators_returns_page() throws Exception {
        OperatorQueryService.OperatorSummary s = new OperatorQueryService.OperatorSummary(
                "op-1", "one@example.com", "One", "ACTIVE", List.of("SUPPORT_LOCK"),
                false, null, Instant.parse("2026-01-01T00:00:00Z"));
        when(queryService.listOperators(any(), anyInt(), anyInt()))
                .thenReturn(new OperatorQueryService.OperatorPage(List.of(s), 1L, 0, 20, 1));

        mockMvc.perform(get("/api/admin/operators").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].operatorId").value("op-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
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
        when(createOperatorUseCase.createOperator(
                anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(new CreateOperatorUseCase.CreateOperatorResult(
                        "op-new", "new@example.com", "New", "ACTIVE",
                        List.of("SUPPORT_LOCK"), false,
                        Instant.parse("2026-04-24T10:00:00Z"),
                        "audit-new"));

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
                                  "roles": ["SUPPORT_LOCK"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operatorId").value("op-new"))
                .andExpect(jsonPath("$.auditId").value("audit-new"));
    }

    @Test
    void create_operator_duplicate_email_returns_409() throws Exception {
        doThrow(new OperatorEmailConflictException("dup"))
                .when(createOperatorUseCase).createOperator(anyString(), anyString(), anyString(), any(), any(), anyString());

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
                                  "roles": []
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_EMAIL_CONFLICT"));
    }

    @Test
    void create_operator_unknown_role_returns_400() throws Exception {
        doThrow(new RoleNotFoundException("GHOST"))
                .when(createOperatorUseCase).createOperator(anyString(), anyString(), anyString(), any(), any(), anyString());

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
                                  "roles": ["GHOST"]
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
                                  "roles": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
                                  "roles": []
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
}
