package com.example.account.presentation;

import com.example.account.application.command.AssignRolesCommand;
import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.result.AssignRolesResult;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.application.result.ProvisionPasswordResetResult;
import com.example.account.application.result.ProvisionedAccountDetailResult;
import com.example.account.application.result.ProvisionedAccountListResult;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.application.service.AssignRolesUseCase;
import com.example.account.application.service.ProvisionAccountUseCase;
import com.example.account.application.service.ProvisionPasswordResetUseCase;
import com.example.account.application.service.ProvisionStatusChangeUseCase;
import com.example.account.application.service.TenantAccountQueryUseCase;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.TenantProvisioningController;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantProvisioningController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("TenantProvisioningController slice tests")
class TenantProvisioningControllerTest {

    private static final String TENANT_ID = "wms";
    private static final String ACCOUNT_ID = "acc-uuid-001";
    private static final Instant NOW = Instant.now();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvisionAccountUseCase provisionAccountUseCase;
    @MockitoBean
    private TenantAccountQueryUseCase tenantAccountQueryUseCase;
    @MockitoBean
    private AssignRolesUseCase assignRolesUseCase;
    @MockitoBean
    private ProvisionStatusChangeUseCase provisionStatusChangeUseCase;
    @MockitoBean
    private ProvisionPasswordResetUseCase provisionPasswordResetUseCase;

    // --- POST /internal/tenants/{tenantId}/accounts ---

    @Test
    @DisplayName("POST create account returns 201 with correct fields, no sensitive data")
    void createAccount_success_returns201() throws Exception {
        given(provisionAccountUseCase.execute(any(ProvisionAccountCommand.class)))
                .willReturn(new ProvisionAccountResult(
                        ACCOUNT_ID, TENANT_ID, "user@example.com", "ACTIVE",
                        List.of("WAREHOUSE_ADMIN"), NOW));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!",
                                  "roles": ["WAREHOUSE_ADMIN"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("WAREHOUSE_ADMIN"))
                .andExpect(jsonPath("$.createdAt").exists())
                // Sensitive fields must NOT be present
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.emailHash").doesNotExist());
    }

    @Test
    @DisplayName("POST create account missing email returns 400 VALIDATION_ERROR")
    void createAccount_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST create account missing password returns 400 VALIDATION_ERROR")
    void createAccount_missingPassword_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST create account invalid email returns 400 VALIDATION_ERROR")
    void createAccount_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST create account tenant scope mismatch returns 403 TENANT_SCOPE_DENIED")
    void createAccount_tenantScopeMismatch_returns403() throws Exception {
        given(provisionAccountUseCase.execute(any()))
                .willThrow(new TenantScopeDeniedException("fan-platform", TENANT_ID));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("POST create account tenant not found returns 404 TENANT_NOT_FOUND")
    void createAccount_tenantNotFound_returns404() throws Exception {
        given(provisionAccountUseCase.execute(any()))
                .willThrow(new TenantNotFoundException("nonexistent"));

        mockMvc.perform(post("/internal/tenants/nonexistent/accounts")
                        .header("X-Tenant-Id", "nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST create account tenant suspended returns 409 TENANT_SUSPENDED")
    void createAccount_tenantSuspended_returns409() throws Exception {
        given(provisionAccountUseCase.execute(any()))
                .willThrow(new TenantSuspendedException(TENANT_ID));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    // --- GET /internal/tenants/{tenantId}/accounts ---

    @Test
    @DisplayName("GET list accounts returns 200 with paginated content")
    void listAccounts_returns200() throws Exception {
        ProvisionedAccountListResult result = new ProvisionedAccountListResult(
                List.of(new ProvisionedAccountListResult.Item(
                        ACCOUNT_ID, TENANT_ID, "user@example.com", "ACTIVE",
                        List.of("WAREHOUSE_ADMIN"), NOW)),
                1L, 0, 20, 1);
        given(tenantAccountQueryUseCase.listAccounts(eq(TENANT_ID), eq(null), eq(0), eq(20)))
                .willReturn(result);

        mockMvc.perform(get("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @DisplayName("GET list accounts size > 100 returns 400")
    void listAccounts_sizeTooLarge_returns400() throws Exception {
        given(tenantAccountQueryUseCase.listAccounts(any(), any(), eq(0), eq(101)))
                .willThrow(new IllegalArgumentException("size must be <= 100"));

        mockMvc.perform(get("/internal/tenants/{tenantId}/accounts", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- GET /internal/tenants/{tenantId}/accounts/{accountId} ---

    @Test
    @DisplayName("GET single account returns 200 without sensitive fields")
    void getAccount_returns200() throws Exception {
        ProvisionedAccountDetailResult result = new ProvisionedAccountDetailResult(
                ACCOUNT_ID, TENANT_ID, "user@example.com", "ACTIVE",
                List.of("WAREHOUSE_ADMIN"), "홍길동", NOW, null);
        given(tenantAccountQueryUseCase.getAccount(eq(TENANT_ID), eq(ACCOUNT_ID)))
                .willReturn(result);

        mockMvc.perform(get("/internal/tenants/{tenantId}/accounts/{accountId}", TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.displayName").value("홍길동"))
                // Sensitive fields must NOT be present
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.emailHash").doesNotExist());
    }

    @Test
    @DisplayName("GET single account not found returns 404")
    void getAccount_notFound_returns404() throws Exception {
        given(tenantAccountQueryUseCase.getAccount(eq(TENANT_ID), eq("nonexistent")))
                .willThrow(new AccountNotFoundException("nonexistent"));

        mockMvc.perform(get("/internal/tenants/{tenantId}/accounts/{accountId}", TENANT_ID, "nonexistent")
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // --- PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles ---

    @Test
    @DisplayName("PATCH roles returns 200 with updated roles")
    void assignRoles_returns200() throws Exception {
        given(assignRolesUseCase.execute(any(AssignRolesCommand.class)))
                .willReturn(new AssignRolesResult(ACCOUNT_ID, TENANT_ID,
                        List.of("INBOUND_OPERATOR", "INVENTORY_VIEWER"), NOW));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": ["INBOUND_OPERATOR", "INVENTORY_VIEWER"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("PATCH roles with empty array removes all roles and returns 200")
    void assignRoles_emptyArray_returns200() throws Exception {
        given(assignRolesUseCase.execute(any(AssignRolesCommand.class)))
                .willReturn(new AssignRolesResult(ACCOUNT_ID, TENANT_ID, List.of(), NOW));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    @DisplayName("PATCH roles with null roles returns 400 VALIDATION_ERROR")
    void assignRoles_nullRoles_returns400() throws Exception {
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/roles",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roles": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status ---

    @Test
    @DisplayName("PATCH status ACTIVE->LOCKED returns 200")
    void changeStatus_lockAccount_returns200() throws Exception {
        given(provisionStatusChangeUseCase.execute(eq(TENANT_ID), eq(ACCOUNT_ID),
                eq(AccountStatus.LOCKED), any()))
                .willReturn(new ProvisionedStatusChangeResult(ACCOUNT_ID, TENANT_ID,
                        "ACTIVE", "LOCKED", NOW));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/status",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOCKED",
                                  "operatorId": "sys-wms-backend"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"))
                .andExpect(jsonPath("$.changedAt").exists());
    }

    @Test
    @DisplayName("PATCH status invalid transition returns 409 STATE_TRANSITION_INVALID")
    void changeStatus_invalidTransition_returns409() throws Exception {
        given(provisionStatusChangeUseCase.execute(any(), any(), any(), any()))
                .willThrow(new StateTransitionException(AccountStatus.DELETED, AccountStatus.LOCKED,
                        StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE));

        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/status",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "LOCKED"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("PATCH status missing status field returns 400")
    void changeStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/internal/tenants/{tenantId}/accounts/{accountId}/status",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorId": "sys-wms-backend"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- POST /internal/tenants/{tenantId}/accounts/{accountId}/password-reset ---

    @Test
    @DisplayName("POST password-reset returns 200")
    void passwordReset_returns200() throws Exception {
        given(provisionPasswordResetUseCase.execute(eq(TENANT_ID), eq(ACCOUNT_ID), any()))
                .willReturn(new ProvisionPasswordResetResult(ACCOUNT_ID, TENANT_ID, NOW,
                        "Password reset token issued. Delivery via existing notification channel."));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts/{accountId}/password-reset",
                        TENANT_ID, ACCOUNT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorId": "sys-wms-backend"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.resetTokenIssuedAt").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST password-reset account not found returns 404")
    void passwordReset_accountNotFound_returns404() throws Exception {
        given(provisionPasswordResetUseCase.execute(any(), any(), any()))
                .willThrow(new AccountNotFoundException("nonexistent"));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts/{accountId}/password-reset",
                        TENANT_ID, "nonexistent")
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // --- Cross-cutting: X-Tenant-Id mismatch ---

    @Test
    @DisplayName("X-Tenant-Id header mismatch returns 403 TENANT_SCOPE_DENIED at controller level")
    void tenantScopeMismatch_returnsControllerLevel403() throws Exception {
        // Controller validates callerTenantId != pathTenantId before calling use case
        mockMvc.perform(post("/internal/tenants/wms/accounts")
                        .header("X-Tenant-Id", "fan-platform")  // mismatch
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }
}
