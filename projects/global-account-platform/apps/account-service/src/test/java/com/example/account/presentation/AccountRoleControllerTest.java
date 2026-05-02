package com.example.account.presentation;

import com.example.account.application.command.AddAccountRoleCommand;
import com.example.account.application.command.RemoveAccountRoleCommand;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountRoleMutationResult;
import com.example.account.application.service.AddAccountRoleUseCase;
import com.example.account.application.service.RemoveAccountRoleUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.role.AccountRoleController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-255: Slice tests for the single-role add/remove endpoints.
 */
@WebMvcTest(AccountRoleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("AccountRoleController slice — TASK-BE-255")
class AccountRoleControllerTest {

    private static final String TENANT_ID = "wms";
    private static final String ACCOUNT_ID = "acc-uuid-001";
    private static final Instant NOW = Instant.now();

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AddAccountRoleUseCase addAccountRoleUseCase;
    @MockitoBean private RemoveAccountRoleUseCase removeAccountRoleUseCase;

    // ─── PATCH /roles:add ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH roles:add 성공 — 200 + 갱신된 roles")
    void addRole_success_returns200() throws Exception {
        given(addAccountRoleUseCase.execute(any(AddAccountRoleCommand.class)))
                .willReturn(new AccountRoleMutationResult(
                        ACCOUNT_ID, TENANT_ID, List.of("WAREHOUSE_ADMIN", "INBOUND_OPERATOR"), NOW, true));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INBOUND_OPERATOR",
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("INBOUND_OPERATOR"))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("PATCH roles:add 멱등 호출 (이미 존재) → 200 with unchanged roles")
    void addRole_alreadyAssigned_idempotentReturns200() throws Exception {
        given(addAccountRoleUseCase.execute(any(AddAccountRoleCommand.class)))
                .willReturn(new AccountRoleMutationResult(
                        ACCOUNT_ID, TENANT_ID, List.of("WAREHOUSE_ADMIN"), NOW, false));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "WAREHOUSE_ADMIN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_ADMIN"));
    }

    @Test
    @DisplayName("PATCH roles:add roleName 누락 → 400 VALIDATION_ERROR")
    void addRole_missingRoleName_returns400() throws Exception {
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorId": "sys-wms"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH roles:add 규격 위반 — IllegalArgumentException → 400 VALIDATION_ERROR")
    void addRole_invalidRoleNamePattern_returns400() throws Exception {
        given(addAccountRoleUseCase.execute(any(AddAccountRoleCommand.class)))
                .willThrow(new IllegalArgumentException(
                        "roleName must match pattern ^[A-Z][A-Z0-9_]*$: lowercase"));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "lowercase"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH roles:add cross-tenant account → 404 ACCOUNT_NOT_FOUND")
    void addRole_crossTenantAccount_returns404() throws Exception {
        given(addAccountRoleUseCase.execute(any(AddAccountRoleCommand.class)))
                .willThrow(new AccountNotFoundException(ACCOUNT_ID));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "ADMIN"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH roles:add tenant scope mismatch → 403 TENANT_SCOPE_DENIED")
    void addRole_tenantScopeMismatch_returns403() throws Exception {
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "ADMIN"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    // ─── PATCH /roles:remove ──────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH roles:remove 성공 — 200 + 갱신된 roles")
    void removeRole_success_returns200() throws Exception {
        given(removeAccountRoleUseCase.execute(any(RemoveAccountRoleCommand.class)))
                .willReturn(new AccountRoleMutationResult(
                        ACCOUNT_ID, TENANT_ID, List.of("WAREHOUSE_ADMIN"), NOW, true));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:remove",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INBOUND_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_ADMIN"));
    }

    @Test
    @DisplayName("PATCH roles:remove 미할당 role 제거 시도 → 200 (멱등)")
    void removeRole_notAssigned_idempotentReturns200() throws Exception {
        given(removeAccountRoleUseCase.execute(any(RemoveAccountRoleCommand.class)))
                .willReturn(new AccountRoleMutationResult(
                        ACCOUNT_ID, TENANT_ID, List.of("WAREHOUSE_ADMIN"), NOW, false));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:remove",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "INVENTORY_VIEWER"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_ADMIN"));
    }

    // TASK-BE-265: operatorId length must mirror granted_by VARCHAR(36).

    @Test
    @DisplayName("PATCH roles:add operatorId 37자 → 400 VALIDATION_ERROR")
    void addRole_operatorIdTooLong_returns400() throws Exception {
        String operatorId = "o".repeat(37);

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:add",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "ADMIN",
                                  "operatorId": "%s"
                                }
                                """.formatted(operatorId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH roles:remove operatorId 37자 → 400 VALIDATION_ERROR")
    void removeRole_operatorIdTooLong_returns400() throws Exception {
        String operatorId = "o".repeat(37);

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles:remove",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roleName": "ADMIN",
                                  "operatorId": "%s"
                                }
                                """.formatted(operatorId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
