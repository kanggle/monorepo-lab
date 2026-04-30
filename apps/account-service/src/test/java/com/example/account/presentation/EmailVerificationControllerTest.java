package com.example.account.presentation;

import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.EmailVerificationTokenInvalidException;
import com.example.account.application.exception.RateLimitedException;
import com.example.account.application.result.VerifyEmailResult;
import com.example.account.application.service.SendVerificationEmailUseCase;
import com.example.account.application.service.VerifyEmailUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link EmailVerificationController} (TASK-BE-114).
 *
 * <p>Covers the full status-code matrix called out in the task spec:
 * 200 OK, 400 TOKEN_EXPIRED_OR_INVALID, 400 VALIDATION_ERROR, 409
 * EMAIL_ALREADY_VERIFIED, 204 No Content, 429 RATE_LIMITED.</p>
 */
@WebMvcTest(EmailVerificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("EmailVerificationController 슬라이스 테스트")
class EmailVerificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VerifyEmailUseCase verifyEmailUseCase;

    @MockitoBean
    private SendVerificationEmailUseCase sendVerificationEmailUseCase;

    // ----------------------------------------------------------------------
    // POST /api/accounts/signup/verify-email
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("verify-email — 유효한 토큰 시 200, accountId/emailVerifiedAt 반환")
    void verifyEmail_validToken_returns200() throws Exception {
        Instant verifiedAt = Instant.parse("2026-04-26T10:00:00Z");
        given(verifyEmailUseCase.execute(anyString()))
                .willReturn(new VerifyEmailResult("acc-1", verifiedAt));

        mockMvc.perform(post("/api/accounts/signup/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "verify-token-uuid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.emailVerifiedAt").value("2026-04-26T10:00:00Z"));

        verify(verifyEmailUseCase).execute("verify-token-uuid");
    }

    @Test
    @DisplayName("verify-email — 만료/존재하지 않는 토큰은 400 TOKEN_EXPIRED_OR_INVALID")
    void verifyEmail_invalidToken_returns400() throws Exception {
        doThrow(new EmailVerificationTokenInvalidException())
                .when(verifyEmailUseCase).execute(anyString());

        mockMvc.perform(post("/api/accounts/signup/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-or-unknown"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED_OR_INVALID"));
    }

    @Test
    @DisplayName("verify-email — 이미 인증된 경우 409 EMAIL_ALREADY_VERIFIED")
    void verifyEmail_alreadyVerified_returns409() throws Exception {
        doThrow(new EmailAlreadyVerifiedException())
                .when(verifyEmailUseCase).execute(anyString());

        mockMvc.perform(post("/api/accounts/signup/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "verify-token-uuid"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    @DisplayName("verify-email — token 누락은 400 VALIDATION_ERROR (use case 미호출)")
    void verifyEmail_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/signup/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ----------------------------------------------------------------------
    // POST /api/accounts/signup/resend-verification-email
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("resend — 정상 호출 시 204")
    void resend_validRequest_returns204() throws Exception {
        doNothing().when(sendVerificationEmailUseCase).execute(anyString());

        mockMvc.perform(post("/api/accounts/signup/resend-verification-email")
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isNoContent());

        verify(sendVerificationEmailUseCase).execute("acc-1");
    }

    @Test
    @DisplayName("resend — 5분 내 재시도는 429 RATE_LIMITED")
    void resend_rateLimited_returns429() throws Exception {
        doThrow(new RateLimitedException("Resend rate limit exceeded — try again later"))
                .when(sendVerificationEmailUseCase).execute(anyString());

        mockMvc.perform(post("/api/accounts/signup/resend-verification-email")
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("resend — 이미 인증된 계정은 409 EMAIL_ALREADY_VERIFIED")
    void resend_alreadyVerified_returns409() throws Exception {
        doThrow(new EmailAlreadyVerifiedException())
                .when(sendVerificationEmailUseCase).execute(anyString());

        mockMvc.perform(post("/api/accounts/signup/resend-verification-email")
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_VERIFIED"));
    }

    @Test
    @DisplayName("resend — X-Account-Id 헤더 누락은 400 VALIDATION_ERROR")
    void resend_missingHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/signup/resend-verification-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
