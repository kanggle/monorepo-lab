package com.example.admin.presentation;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountResult;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.client.AccountServiceClient;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AccountAdminControllerTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class AccountAdminControllerTest {

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
    AccountAdminUseCase useCase;

    @MockBean
    BulkLockAccountUseCase bulkLockUseCase;

    @MockBean
    AccountServiceClient accountServiceClient;

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
        return "Bearer " + jwt.operatorToken("op-1");
    }

    @Test
    void lock_success_returns_200() throws Exception {
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", "op-1", Instant.parse("2026-01-01T00:00:00Z"), "audit-1"));

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-1")
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"))
                .andExpect(jsonPath("$.auditId").value("audit-1"));
    }

    @Test
    void lock_missing_idempotency_key_returns_400_validation_error() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void lock_missing_operator_reason_returns_400_reason_required() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void lock_without_required_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-3")
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void lock_without_jwt_returns_401_token_invalid() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Idempotency-Key", "idemp-4")
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // ---- search (GET /api/admin/accounts) ----

    @Test
    void search_superAdmin_noEmail_returns_paginated_list() throws Exception {
        var items = List.of(new AccountServiceClient.AccountSummaryItem(
                "acc-1", "a@example.com", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z")));
        var page = new AccountServiceClient.AccountSearchResponse(items, 1L, 0, 20, 1);
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
        when(accountServiceClient.listAll(anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("acc-1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void search_noEmail_noAccountReadPermission_returns_empty() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);

        mockMvc.perform(get("/api/admin/accounts")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void search_withEmail_delegates_to_search_client() throws Exception {
        var items = List.of(new AccountServiceClient.AccountSummaryItem(
                "acc-1", "a@example.com", "ACTIVE", Instant.parse("2026-01-01T00:00:00Z")));
        var result = new AccountServiceClient.AccountSearchResponse(items, 1L, 0, 20, 1);
        when(accountServiceClient.search("a@example.com")).thenReturn(result);

        mockMvc.perform(get("/api/admin/accounts?email=a@example.com")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("a@example.com"));
    }
}
