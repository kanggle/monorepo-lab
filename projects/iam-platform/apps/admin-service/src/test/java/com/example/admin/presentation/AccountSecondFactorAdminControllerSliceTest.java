package com.example.admin.presentation;

import com.example.admin.application.AccountSecondFactorResetUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ResetAccountSecondFactorCommand;
import com.example.admin.application.ResetAccountSecondFactorResult;
import com.example.admin.application.exception.AccountSecondFactorNotEnrolledException;
import com.example.admin.application.exception.TargetAccountNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S6 — {@code POST /api/admin/accounts/{accountId}/2fa/reset} through the real
 * {@link RequiresPermissionAspect} + {@link AdminExceptionHandler}: the permission gate, the two reason
 * requirements, the Idempotency-Key requirement and the contract's error codes. The role → permission mapping
 * itself (which seed roles hold the key) is the IT's ({@code AccountSecondFactorResetIntegrationTest}, real V0049).
 */
@WebMvcTest(controllers = AccountSecondFactorAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AccountSecondFactorAdminControllerSliceTest.JwtBeans.class})
@DisplayName("AccountSecondFactorAdminController slice (TASK-MONO-771 S6)")
class AccountSecondFactorAdminControllerSliceTest {

    private static final String PATH = "/api/admin/accounts/acc-s6/2fa/reset";
    private static final String BODY = "{\"reason\":\"본인 확인: 신분증 대조\",\"ticketId\":\"T-1\"}";

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

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountSecondFactorResetUseCase useCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;

    private void granted(boolean yes) {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(yes);
    }

    private ResultActions call(String headerReason, String idempotencyKey, String body) throws Exception {
        var req = post(PATH)
                .header("Authorization", "Bearer " + jwt.operatorToken("op-s6"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (headerReason != null) req = req.header("X-Operator-Reason", headerReason);
        if (idempotencyKey != null) req = req.header("Idempotency-Key", idempotencyKey);
        return mockMvc.perform(req);
    }

    @Test
    @DisplayName("200 — 응답 계약 모양 · 감사 사유 = body reason · X-Tenant-Id 는 읽지 않는다")
    void success() throws Exception {
        granted(true);
        when(useCase.reset(any())).thenReturn(new ResetAccountSecondFactorResult(
                "acc-s6", "op-s6", Instant.parse("2026-10-09T03:00:00Z"), "audit-1"));

        call("lost-device", "idem-1", BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-s6"))
                .andExpect(jsonPath("$.operatorId").value("op-s6"))
                .andExpect(jsonPath("$.resetAt").value("2026-10-09T03:00:00Z"))
                .andExpect(jsonPath("$.auditId").value("audit-1"));

        ArgumentCaptor<ResetAccountSecondFactorCommand> cmd = ArgumentCaptor.forClass(ResetAccountSecondFactorCommand.class);
        verify(useCase).reset(cmd.capture());
        assertThat(cmd.getValue().reason()).isEqualTo("본인 확인: 신분증 대조");
        assertThat(cmd.getValue().ticketId()).isEqualTo("T-1");
        assertThat(cmd.getValue().idempotencyKey()).isEqualTo("idem-1");
        assertThat(cmd.getValue().operator().operatorId()).isEqualTo("op-s6");
    }

    @Test
    @DisplayName("🔴 account.2fa_reset 미보유 → 403 PERMISSION_DENIED · 리셋 미실행 · 그 키로 판정")
    void noPermission_403() throws Exception {
        granted(false);

        call("lost-device", "idem-1", BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(permissionEvaluator).hasPermission("op-s6", Permission.ACCOUNT_2FA_RESET);
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("🔴 X-Operator-Reason 헤더 없음 → 400 REASON_REQUIRED (body reason 이 있어도) · 리셋 미실행")
    void missingReasonHeader_400() throws Exception {
        granted(true);

        call(null, "idem-1", BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("body reason 없음 → 400 REASON_REQUIRED (헤더가 있어도) · 리셋 미실행")
    void missingBodyReason_400() throws Exception {
        granted(true);

        call("lost-device", "idem-1", "{\"ticketId\":\"T-1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("Idempotency-Key 없음 → 400 VALIDATION_ERROR · 리셋 미실행")
    void missingIdempotencyKey_400() throws Exception {
        granted(true);

        call("lost-device", null, BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("토큰 없음 → 401 TOKEN_INVALID")
    void noJwt_401() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-Operator-Reason", "lost-device")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("플랫폼 범위 아님 → 403 TENANT_SCOPE_DENIED")
    void notPlatform_403() throws Exception {
        granted(true);
        when(useCase.reset(any())).thenThrow(new TenantScopeDeniedException("platform only"));

        call("lost-device", "idem-1", BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }

    @Test
    @DisplayName("등록 없음 → 404 TOTP_NOT_ENROLLED (리셋 전용 문구 — 재발급 문구 아님)")
    void notEnrolled_404() throws Exception {
        granted(true);
        when(useCase.reset(any())).thenThrow(
                new AccountSecondFactorNotEnrolledException("The account has no second-factor enrolment to reset"));

        call("lost-device", "idem-1", BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOTP_NOT_ENROLLED"))
                .andExpect(jsonPath("$.message").value("The account has no second-factor enrolment to reset"));
    }

    @Test
    @DisplayName("계정 없음 → 404 ACCOUNT_NOT_FOUND")
    void accountNotFound_404() throws Exception {
        granted(true);
        when(useCase.reset(any())).thenThrow(new TargetAccountNotFoundException("Target account not found", null));

        call("lost-device", "idem-1", BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }
}
