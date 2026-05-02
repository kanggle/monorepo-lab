package com.example.account.presentation;

import com.example.account.application.exception.BulkLimitExceededException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantScopeDeniedException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.result.BulkProvisionAccountResult;
import com.example.account.application.service.BulkProvisionAccountUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.BulkAccountController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BulkAccountController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("BulkAccountController slice tests — TASK-BE-257")
class BulkAccountControllerTest {

    private static final String TENANT_ID = "wms";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BulkProvisionAccountUseCase bulkProvisionAccountUseCase;

    // ── Success ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST :bulk 성공 — 200 + partial-success summary")
    void bulkCreate_success_returns200() throws Exception {
        BulkProvisionAccountResult result = BulkProvisionAccountResult.of(
                List.of(new BulkProvisionAccountResult.CreatedItem("ext-001", "acc-uuid-001")),
                List.of(),
                1);
        given(bulkProvisionAccountUseCase.execute(any())).willReturn(result);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {
                                      "externalId": "ext-001",
                                      "email": "user@example.com",
                                      "displayName": "홍길동",
                                      "roles": ["WAREHOUSE_ADMIN"],
                                      "status": "ACTIVE"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created[0].externalId").value("ext-001"))
                .andExpect(jsonPath("$.created[0].accountId").value("acc-uuid-001"))
                .andExpect(jsonPath("$.failed").isArray())
                .andExpect(jsonPath("$.summary.requested").value(1))
                .andExpect(jsonPath("$.summary.created").value(1))
                .andExpect(jsonPath("$.summary.failed").value(0));
    }

    @Test
    @DisplayName("부분 실패 — 200 + failed 항목에 errorCode 포함")
    void bulkCreate_partialFailure_returns200WithFailedItems() throws Exception {
        BulkProvisionAccountResult result = BulkProvisionAccountResult.of(
                List.of(new BulkProvisionAccountResult.CreatedItem("ext-001", "acc-uuid-001")),
                List.of(new BulkProvisionAccountResult.FailedItem("ext-002", "EMAIL_DUPLICATE",
                        "Email already exists within the tenant: dup@example.com")),
                2);
        given(bulkProvisionAccountUseCase.execute(any())).willReturn(result);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "email": "ok@example.com", "externalId": "ext-001" },
                                    { "email": "dup@example.com", "externalId": "ext-002" }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.requested").value(2))
                .andExpect(jsonPath("$.summary.created").value(1))
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.failed[0].errorCode").value("EMAIL_DUPLICATE"))
                .andExpect(jsonPath("$.failed[0].externalId").value("ext-002"));
    }

    @Test
    @DisplayName("빈 items 배열 — 200 + 빈 결과")
    void bulkCreate_emptyItems_returns200EmptyResult() throws Exception {
        BulkProvisionAccountResult result = BulkProvisionAccountResult.of(List.of(), List.of(), 0);
        given(bulkProvisionAccountUseCase.execute(any())).willReturn(result);

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.requested").value(0))
                .andExpect(jsonPath("$.summary.created").value(0))
                .andExpect(jsonPath("$.summary.failed").value(0));
    }

    // ── Request-level validation errors ───────────────────────────────────────

    @Test
    @DisplayName("items null — 400 VALIDATION_ERROR")
    void bulkCreate_nullItems_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": null }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("items 항목 email null — 400 VALIDATION_ERROR")
    void bulkCreate_itemMissingEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "displayName": "No Email" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("items 항목 email 형식 오류 — 400 VALIDATION_ERROR")
    void bulkCreate_itemInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "email": "not-an-email" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("status 유효하지 않은 값 — 400 VALIDATION_ERROR")
    void bulkCreate_invalidStatus_returns400() throws Exception {
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    { "email": "user@example.com", "status": "INVALID_STATUS" }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ── Tenant errors ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("X-Tenant-Id 불일치 — 403 TENANT_SCOPE_DENIED (controller 레벨)")
    void bulkCreate_tenantScopeMismatch_returns403() throws Exception {
        mockMvc.perform(post("/internal/tenants/wms/accounts:bulk")
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("use case에서 TenantScopeDeniedException — 403")
    void bulkCreate_useCaseTenantScopeDenied_returns403() throws Exception {
        given(bulkProvisionAccountUseCase.execute(any()))
                .willThrow(new TenantScopeDeniedException("fan-platform", TENANT_ID));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("테넌트 없음 — 404 TENANT_NOT_FOUND")
    void bulkCreate_tenantNotFound_returns404() throws Exception {
        given(bulkProvisionAccountUseCase.execute(any()))
                .willThrow(new TenantNotFoundException("nonexistent"));

        mockMvc.perform(post("/internal/tenants/nonexistent/accounts:bulk")
                        .header("X-Tenant-Id", "nonexistent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("테넌트 SUSPENDED — 409 TENANT_SUSPENDED")
    void bulkCreate_tenantSuspended_returns409() throws Exception {
        given(bulkProvisionAccountUseCase.execute(any()))
                .willThrow(new TenantSuspendedException(TENANT_ID));

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_SUSPENDED"));
    }

    // ── Bulk limit ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("use case에서 BulkLimitExceededException — 400 BULK_LIMIT_EXCEEDED")
    void bulkCreate_limitExceeded_returns400() throws Exception {
        given(bulkProvisionAccountUseCase.execute(any()))
                .willThrow(new BulkLimitExceededException(1001, 1000));

        // Build a request with a single item (Bean Validation passes); the use case throws
        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "items": [{ "email": "user@example.com" }] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BULK_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("1001건 items → Bean Validation 위반이 BULK_LIMIT_EXCEEDED 로 라우팅 — TASK-BE-271")
    void bulkCreate_1001ItemsBeanValidation_returnsBulkLimitExceeded() throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 1001; i++) {
            if (i > 0) items.append(",");
            items.append("{\"email\":\"user").append(i).append("@example.com\"}");
        }

        mockMvc.perform(post("/internal/tenants/{tenantId}/accounts:bulk", TENANT_ID)
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [" + items + "] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BULK_LIMIT_EXCEEDED"));
    }
}
