package com.example.account.presentation;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.AccountStatusWithTenantResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.application.service.SocialSignupUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.AccountLockController;
import com.example.account.presentation.internal.AccountStatusQueryController;
import com.example.account.presentation.internal.SocialSignupController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AccountStatusQueryController.class, AccountLockController.class, SocialSignupController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("Internal Controller slice tests")
class InternalControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountStatusUseCase accountStatusUseCase;

    @MockitoBean
    private SocialSignupUseCase socialSignupUseCase;

    @Test
    @DisplayName("GET /internal/accounts/{id}/status returns 200")
    void getStatus_validId_returns200() throws Exception {
        given(accountStatusUseCase.getStatus(eq("acc-123")))
                .willReturn(new AccountStatusResult("acc-123", "ACTIVE", Instant.now(), null));

        mockMvc.perform(get("/internal/accounts/acc-123/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("TASK-BE-600: GET /internal/accounts/{id}/status with X-Tenant-Id reads that tenant")
    void getStatus_withTenantHeader_readsThatTenant() throws Exception {
        given(accountStatusUseCase.getStatus(eq("acc-123"), eq(new TenantId("ecommerce"))))
                .willReturn(new AccountStatusResult("acc-123", "LOCKED", Instant.now(), null));

        mockMvc.perform(get("/internal/accounts/acc-123/status")
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // The header-less (fan-platform-pinned) overload is NOT what answered.
        verify(accountStatusUseCase, never()).getStatus(anyString());
    }

    @Test
    @DisplayName("TASK-BE-600: an account absent from the header's tenant → 404 ACCOUNT_NOT_FOUND")
    void getStatus_withTenantHeader_notInTenant_returns404() throws Exception {
        given(accountStatusUseCase.getStatus(eq("acc-123"), eq(new TenantId("ecommerce"))))
                .willThrow(new AccountNotFoundException("acc-123"));

        mockMvc.perform(get("/internal/accounts/acc-123/status")
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("TASK-BE-602: GET /internal/accounts/{id}/status-with-tenant → 200 with the account's own tenantId")
    void getStatusWithTenant_returnsTenantAndStatus() throws Exception {
        Instant changedAt = Instant.parse("2026-09-25T00:00:00Z");
        given(accountStatusUseCase.getStatusResolvingTenant(eq("acc-123")))
                .willReturn(new AccountStatusWithTenantResult("acc-123", "ecommerce", "LOCKED", changedAt));

        mockMvc.perform(get("/internal/accounts/acc-123/status-with-tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-123"))
                .andExpect(jsonPath("$.tenantId").value("ecommerce"))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.statusChangedAt").exists())
                // No PII on this internal read.
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("TASK-BE-602: status-with-tenant ignores X-Tenant-Id — the tenant is the output, not an input")
    void getStatusWithTenant_ignoresTenantHeader() throws Exception {
        given(accountStatusUseCase.getStatusResolvingTenant(eq("acc-123")))
                .willReturn(new AccountStatusWithTenantResult("acc-123", "fan-platform", "ACTIVE", Instant.now()));

        mockMvc.perform(get("/internal/accounts/acc-123/status-with-tenant")
                        .header("X-Tenant-Id", "ecommerce"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("fan-platform"));

        verify(accountStatusUseCase, never()).getStatus(anyString());
        verify(accountStatusUseCase, never()).getStatus(anyString(), any());
    }

    @Test
    @DisplayName("TASK-BE-602: status-with-tenant — no tenant holds the id → 404 ACCOUNT_NOT_FOUND")
    void getStatusWithTenant_absent_returns404() throws Exception {
        given(accountStatusUseCase.getStatusResolvingTenant(eq("missing")))
                .willThrow(new AccountNotFoundException("missing"));

        mockMvc.perform(get("/internal/accounts/missing/status-with-tenant"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/lock returns 200")
    void lockAccount_validRequest_returns200() throws Exception {
        // TASK-MONO-735: no X-Tenant-Id → the account's own tenant (row lookup).
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willReturn(new StatusChangeResult("acc-123", "ACTIVE", "LOCKED", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-123/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_LOCK",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/lock DELETED account returns 409")
    void lockAccount_deletedAccount_returns409() throws Exception {
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willThrow(new StateTransitionException(AccountStatus.DELETED, AccountStatus.LOCKED,
                        StatusChangeReason.ADMIN_LOCK));

        mockMvc.perform(post("/internal/accounts/acc-123/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_LOCK",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/unlock returns 200")
    void unlockAccount_validRequest_returns200() throws Exception {
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willReturn(new StatusChangeResult("acc-123", "LOCKED", "ACTIVE", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-123/unlock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_UNLOCK",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("LOCKED"))
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/delete returns 202")
    void deleteAccount_validRequest_returns202() throws Exception {
        Instant gracePeriodEndsAt = Instant.now().plusSeconds(30 * 24 * 3600L);
        given(accountStatusUseCase.deleteAccountResolvingTenant(eq("acc-123"), eq(StatusChangeReason.ADMIN_DELETE),
                eq("operator"), eq("op-1")))
                .willReturn(new DeleteAccountResult("acc-123", "ACTIVE", "DELETED", gracePeriodEndsAt));

        mockMvc.perform(post("/internal/accounts/acc-123/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_DELETE",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accountId").value("acc-123"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("DELETED"))
                .andExpect(jsonPath("$.gracePeriodEndsAt").exists());
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/delete already DELETED returns 409")
    void deleteAccount_alreadyDeleted_returns409() throws Exception {
        given(accountStatusUseCase.deleteAccountResolvingTenant(eq("acc-123"), eq(StatusChangeReason.ADMIN_DELETE),
                eq("operator"), eq("op-1")))
                .willThrow(new StateTransitionException(AccountStatus.DELETED, AccountStatus.DELETED,
                        StatusChangeReason.ADMIN_DELETE));

        mockMvc.perform(post("/internal/accounts/acc-123/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_DELETE",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("POST /internal/accounts/{id}/delete not found returns 404")
    void deleteAccount_notFound_returns404() throws Exception {
        given(accountStatusUseCase.deleteAccountResolvingTenant(eq("acc-999"), eq(StatusChangeReason.ADMIN_DELETE),
                eq("operator"), eq("op-1")))
                .willThrow(new AccountNotFoundException("acc-999"));

        mockMvc.perform(post("/internal/accounts/acc-999/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ADMIN_DELETE",
                                  "operatorId": "op-1"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    // --- TASK-MONO-735: which lookup /lock · /unlock · /delete use ---

    private static final String LOCK_BODY = """
            {"reason": "AUTO_DETECT", "ruleCode": "TOKEN_REUSE", "riskScore": 90}
            """;

    @Test
    @DisplayName("TASK-MONO-735: lock 헤더 없음 → 계정 행의 테넌트로 찾는다 (fan-platform 고정 아님)")
    void lock_noTenantHeader_resolvesTenantFromRow() throws Exception {
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willReturn(new StatusChangeResult("acc-ec", "ACTIVE", "LOCKED", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-ec/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("LOCKED"));

        verify(accountStatusUseCase, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("TASK-MONO-735: lock X-Tenant-Id='*' (SUPER_ADMIN) → 계정 행의 테넌트로 찾는다")
    void lock_wildcardTenantHeader_resolvesTenantFromRow() throws Exception {
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willReturn(new StatusChangeResult("acc-ec", "ACTIVE", "LOCKED", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-ec/lock")
                        .header("X-Tenant-Id", "*")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk());

        verify(accountStatusUseCase, never()).changeStatus(any(), any());
    }

    @Test
    @DisplayName("TASK-MONO-735 대조군: lock X-Tenant-Id=ecommerce → 그 테넌트로 한정 (계정 행 해소 안 씀)")
    void lock_concreteTenantHeader_staysConfined() throws Exception {
        given(accountStatusUseCase.changeStatus(any(), eq(new TenantId("ecommerce"))))
                .willReturn(new StatusChangeResult("acc-ec", "ACTIVE", "LOCKED", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-ec/lock")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isOk());

        verify(accountStatusUseCase, never()).changeStatusResolvingTenant(any());
    }

    @Test
    @DisplayName("TASK-MONO-735 대조군: lock X-Tenant-Id=fan-platform + 계정이 다른 테넌트 → 404 (격리 유지)")
    void lock_concreteTenantHeader_crossTenant_returns404() throws Exception {
        given(accountStatusUseCase.changeStatus(any(), eq(TenantId.FAN_PLATFORM)))
                .willThrow(new AccountNotFoundException("acc-ec"));

        mockMvc.perform(post("/internal/accounts/acc-ec/lock")
                        .header("X-Tenant-Id", "fan-platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOCK_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        verify(accountStatusUseCase, never()).changeStatusResolvingTenant(any());
    }

    @Test
    @DisplayName("TASK-MONO-735: unlock 헤더 없음 → 계정 행 · 헤더 있음 → 한정")
    void unlock_splitsOnTenantHeader() throws Exception {
        given(accountStatusUseCase.changeStatusResolvingTenant(any()))
                .willReturn(new StatusChangeResult("acc-ec", "LOCKED", "ACTIVE", Instant.now()));
        given(accountStatusUseCase.changeStatus(any(), eq(new TenantId("ecommerce"))))
                .willReturn(new StatusChangeResult("acc-ec", "LOCKED", "ACTIVE", Instant.now()));
        String body = """
                {"reason": "ADMIN_UNLOCK", "operatorId": "op-1"}
                """;

        mockMvc.perform(post("/internal/accounts/acc-ec/unlock")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/accounts/acc-ec/unlock")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(accountStatusUseCase).changeStatusResolvingTenant(any());
        verify(accountStatusUseCase).changeStatus(any(), eq(new TenantId("ecommerce")));
    }

    @Test
    @DisplayName("TASK-MONO-735 대조군: delete X-Tenant-Id=ecommerce → 그 테넌트로 한정")
    void delete_concreteTenantHeader_staysConfined() throws Exception {
        given(accountStatusUseCase.deleteAccount(eq("acc-ec"), eq(StatusChangeReason.ADMIN_DELETE),
                eq("operator"), eq("op-1"), eq(new TenantId("ecommerce"))))
                .willReturn(new DeleteAccountResult("acc-ec", "ACTIVE", "DELETED", Instant.now()));

        mockMvc.perform(post("/internal/accounts/acc-ec/delete")
                        .header("X-Tenant-Id", "ecommerce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "ADMIN_DELETE", "operatorId": "op-1"}
                                """))
                .andExpect(status().isAccepted());

        verify(accountStatusUseCase, never())
                .deleteAccountResolvingTenant(anyString(), any(), anyString(), anyString());
    }

    // --- Social Signup ---

    @Test
    @DisplayName("POST /internal/accounts/social-signup new email returns 201")
    void socialSignup_newEmail_returns201() throws Exception {
        given(socialSignupUseCase.execute(any()))
                .willReturn(new SocialSignupResult("acc-new", "new@example.com", "ACTIVE", true));

        mockMvc.perform(post("/internal/accounts/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new@example.com",
                                  "provider": "GOOGLE",
                                  "providerUserId": "google-123",
                                  "displayName": "John Doe"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-new"))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /internal/accounts/social-signup existing email returns 200")
    void socialSignup_existingEmail_returns200() throws Exception {
        given(socialSignupUseCase.execute(any()))
                .willReturn(new SocialSignupResult("acc-existing", "existing@example.com", "ACTIVE", false));

        mockMvc.perform(post("/internal/accounts/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "existing@example.com",
                                  "provider": "KAKAO",
                                  "providerUserId": "kakao-456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-existing"))
                .andExpect(jsonPath("$.email").value("existing@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /internal/accounts/social-signup missing email returns 400")
    void socialSignup_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/internal/accounts/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "GOOGLE",
                                  "providerUserId": "google-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /internal/accounts/social-signup missing provider returns 400")
    void socialSignup_missingProvider_returns400() throws Exception {
        mockMvc.perform(post("/internal/accounts/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "providerUserId": "google-123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /internal/accounts/social-signup existing locked account returns LOCKED status")
    void socialSignup_lockedAccount_returnsLockedStatus() throws Exception {
        given(socialSignupUseCase.execute(any()))
                .willReturn(new SocialSignupResult("acc-locked", "locked@example.com", "LOCKED", false));

        mockMvc.perform(post("/internal/accounts/social-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "locked@example.com",
                                  "provider": "GOOGLE",
                                  "providerUserId": "google-789",
                                  "displayName": "Locked User"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));
    }
}
