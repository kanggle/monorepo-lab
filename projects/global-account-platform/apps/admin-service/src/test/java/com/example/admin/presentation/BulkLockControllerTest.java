package com.example.admin.presentation;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.BulkLockAccountResult;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.exception.BatchSizeExceededException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
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

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        BulkLockControllerTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class BulkLockControllerTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() { jwt = new OperatorJwtTestFixture(); }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtBeans {
        @Bean JwtVerifier operatorJwtVerifier() {
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

    @BeforeEach
    void grantAll() {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);
    }

    private String bearer() { return "Bearer " + jwt.operatorToken("op-1"); }

    @Test
    void bulk_lock_success_returns_200_with_per_row_outcomes() throws Exception {
        when(bulkLockUseCase.execute(any())).thenReturn(new BulkLockAccountResult(List.of(
                new BulkLockAccountResult.Item("acc-1", "LOCKED", null, null),
                new BulkLockAccountResult.Item("acc-2", "NOT_FOUND", "ACCOUNT_NOT_FOUND", "nope")
        ), false));

        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-ok")
                        .header("X-Operator-Reason", "incident-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountIds":["acc-1","acc-2"],"reason":"fraud-wave"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].accountId").value("acc-1"))
                .andExpect(jsonPath("$.results[0].outcome").value("LOCKED"))
                .andExpect(jsonPath("$.results[1].outcome").value("NOT_FOUND"))
                .andExpect(jsonPath("$.results[1].error.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void bulk_lock_over_cap_returns_422_batch_size_exceeded() throws Exception {
        when(bulkLockUseCase.execute(any())).thenThrow(
                new BatchSizeExceededException("Batch exceeds maximum of 100 accountIds"));

        StringBuilder ids = new StringBuilder("[");
        for (int i = 0; i < 101; i++) { if (i > 0) ids.append(","); ids.append("\"a-").append(i).append("\""); }
        ids.append("]");

        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-overcap")
                        .header("X-Operator-Reason", "incident-43")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":" + ids + ",\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BATCH_SIZE_EXCEEDED"));
    }

    @Test
    void bulk_lock_short_reason_returns_400_validation_error() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-short")
                        .header("X-Operator-Reason", "incident-44")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"a\"],\"reason\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bulk_lock_missing_idempotency_key_returns_400() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "incident-45")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"a\"],\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bulk_lock_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-denied")
                        .header("X-Operator-Reason", "incident-46")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"a\"],\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void bulk_lock_idempotency_key_over_64_chars_returns_400_validation_error() throws Exception {
        String longKey = "k".repeat(65);
        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", longKey)
                        .header("X-Operator-Reason", "incident-size")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"a\"],\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bulk_lock_empty_account_ids_returns_400_validation_error() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-empty")
                        .header("X-Operator-Reason", "incident-empty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[],\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void bulk_lock_idempotency_conflict_returns_409() throws Exception {
        when(bulkLockUseCase.execute(any())).thenThrow(
                new IdempotencyKeyConflictException("payload mismatch"));

        mockMvc.perform(post("/api/admin/accounts/bulk-lock")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-conflict")
                        .header("X-Operator-Reason", "incident-47")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"a\"],\"reason\":\"fraud-wave\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }
}
