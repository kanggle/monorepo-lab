package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.DataExportResult;
import com.example.admin.application.GdprAdminUseCase;
import com.example.admin.application.GdprDeleteCommand;
import com.example.admin.application.GdprDeleteResult;
import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.AuditFailureException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminGdprController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminGdprControllerTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
@DisplayName("AdminGdprController 슬라이스 테스트")
class AdminGdprControllerTest {

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
    GdprAdminUseCase useCase;

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

    // ---------------------------------------------------------------------
    // POST /api/admin/accounts/{accountId}/gdpr-delete
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("POST gdpr-delete: X-Operator-Reason 헤더로 reason 을 전달하면 200 과 GdprDeleteResponse JSON 을 반환한다")
    void gdpr_delete_with_header_reason_returns_200_with_response_body() throws Exception {
        Instant maskedAt = Instant.parse("2026-04-25T10:00:00Z");
        when(useCase.gdprDelete(any(GdprDeleteCommand.class)))
                .thenReturn(new GdprDeleteResult("acc-1", "DELETED", maskedAt, "audit-1"));

        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-gdpr-1")
                        .header("X-Operator-Reason", "subject erasure request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.maskedAt").value("2026-04-25T10:00:00Z"))
                .andExpect(jsonPath("$.auditId").value("audit-1"));
    }

    @Test
    @DisplayName("POST gdpr-delete: body.reason 으로 reason 을 전달해도 동일하게 200 처리된다")
    void gdpr_delete_with_body_reason_returns_200() throws Exception {
        Instant maskedAt = Instant.parse("2026-04-25T10:00:00Z");
        when(useCase.gdprDelete(any(GdprDeleteCommand.class)))
                .thenReturn(new GdprDeleteResult("acc-1", "DELETED", maskedAt, "audit-2"));

        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-gdpr-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"user requested erasure\",\"ticketId\":\"T-99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.status").value("DELETED"))
                .andExpect(jsonPath("$.auditId").value("audit-2"));
    }

    @Test
    @DisplayName("POST gdpr-delete: Idempotency-Key 헤더가 없으면 400 VALIDATION_ERROR 를 반환한다")
    void gdpr_delete_missing_idempotency_key_returns_400_validation_error() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "subject erasure request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST gdpr-delete: 헤더와 body 모두 reason 이 없으면 400 REASON_REQUIRED 를 반환한다")
    void gdpr_delete_missing_reason_in_header_and_body_returns_400_reason_required() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-gdpr-no-reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("POST gdpr-delete: ACCOUNT_LOCK 권한이 없으면 403 PERMISSION_DENIED 를 반환한다")
    void gdpr_delete_without_account_lock_permission_returns_403() throws Exception {
        // SECURITY_ANALYST role: only AUDIT_READ; no account.lock.
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.ACCOUNT_LOCK)))
                .thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-gdpr-deny")
                        .header("X-Operator-Reason", "subject erasure request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("POST gdpr-delete: 감사 기록 실패 시 500 AUDIT_FAILURE 를 반환한다")
    void gdpr_delete_audit_failure_returns_500_audit_failure() throws Exception {
        when(useCase.gdprDelete(any(GdprDeleteCommand.class)))
                .thenThrow(new AuditFailureException("db down", new RuntimeException()));

        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-gdpr-audit-fail")
                        .header("X-Operator-Reason", "subject erasure request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("AUDIT_FAILURE"));
    }

    @Test
    @DisplayName("POST gdpr-delete: JWT 가 없으면 401 TOKEN_INVALID 를 반환한다")
    void gdpr_delete_without_jwt_returns_401_token_invalid() throws Exception {
        mockMvc.perform(post("/api/admin/accounts/acc-1/gdpr-delete")
                        .header("Idempotency-Key", "idemp-gdpr-no-jwt")
                        .header("X-Operator-Reason", "subject erasure request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    // ---------------------------------------------------------------------
    // GET /api/admin/accounts/{accountId}/export
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("GET export: X-Operator-Reason 헤더가 있으면 200 과 DataExportResponse JSON 을 반환한다 (profile 포함)")
    void export_with_profile_returns_200_with_full_response() throws Exception {
        Instant exportedAt = Instant.parse("2026-04-25T10:00:00Z");
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        DataExportResult.ProfileData profile = new DataExportResult.ProfileData(
                "Jane", "+82-10-0000-0000", "1990-01-15", "ko-KR", "Asia/Seoul");
        when(useCase.dataExport(anyString(), any(OperatorContext.class), anyString()))
                .thenReturn(new DataExportResult(
                        "acc-1", "user@example.com", "ACTIVE", createdAt, profile, exportedAt));

        mockMvc.perform(get("/api/admin/accounts/acc-1/export")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "subject access request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.profile.displayName").value("Jane"))
                .andExpect(jsonPath("$.profile.phoneNumber").value("+82-10-0000-0000"))
                .andExpect(jsonPath("$.profile.birthDate").value("1990-01-15"))
                .andExpect(jsonPath("$.profile.locale").value("ko-KR"))
                .andExpect(jsonPath("$.profile.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.exportedAt").value("2026-04-25T10:00:00Z"));
    }

    @Test
    @DisplayName("GET export: profile 이 null 이어도 200 으로 응답하며 profile 필드는 null 로 직렬화된다")
    void export_without_profile_returns_200_with_null_profile() throws Exception {
        Instant exportedAt = Instant.parse("2026-04-25T10:00:00Z");
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(useCase.dataExport(anyString(), any(OperatorContext.class), anyString()))
                .thenReturn(new DataExportResult(
                        "acc-1", "user@example.com", "ACTIVE", createdAt, null, exportedAt));

        mockMvc.perform(get("/api/admin/accounts/acc-1/export")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "subject access request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.profile").doesNotExist());
    }

    @Test
    @DisplayName("GET export: X-Operator-Reason 헤더가 없으면 400 REASON_REQUIRED 를 반환한다")
    void export_missing_reason_header_returns_400_reason_required() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/acc-1/export")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("GET export: AUDIT_READ 권한이 없으면 403 PERMISSION_DENIED 를 반환한다")
    void export_without_audit_read_permission_returns_403() throws Exception {
        // SUPPORT_LOCK role: only ACCOUNT_LOCK; no audit.read.
        when(permissionEvaluator.hasPermission(anyString(), eq(Permission.AUDIT_READ)))
                .thenReturn(false);

        mockMvc.perform(get("/api/admin/accounts/acc-1/export")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "subject access request"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("GET export: JWT 가 없으면 401 TOKEN_INVALID 를 반환한다")
    void export_without_jwt_returns_401_token_invalid() throws Exception {
        mockMvc.perform(get("/api/admin/accounts/acc-1/export")
                        .header("X-Operator-Reason", "subject access request"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
