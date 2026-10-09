package com.example.auth.presentation;

import com.example.auth.application.AccountSecondFactorResetUseCase;
import com.example.auth.application.AccountSecondFactorResetUseCase.Outcome;
import com.example.auth.application.AccountSecondFactorResetUseCase.Result;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S6 — {@code POST /internal/auth/accounts/{accountId}/second-factor/reset} (admin-to-auth.md):
 * the use-case outcome → HTTP mapping. The {@code /internal/auth/**} JWT gate is
 * {@link InternalSecondFactorResetAuthSliceTest}'s; here the dev/test bypass reaches the handler.
 */
@WebMvcTest(InternalSecondFactorResetController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("InternalSecondFactorResetController slice tests (TASK-MONO-771 S6)")
class InternalSecondFactorResetControllerSliceTest {

    private static final String ACCOUNT = "acc-s6";
    private static final String PATH = "/internal/auth/accounts/" + ACCOUNT + "/second-factor/reset";
    private static final String BODY = "{\"reason\":\"lost device\",\"operatorId\":\"op-1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountSecondFactorResetUseCase useCase;

    @Test
    @DisplayName("RESET → 200 {accountId, resetAt, wasConfirmed} · X-Operator-ID 가 운영자")
    void reset_200() throws Exception {
        given(useCase.reset(ACCOUNT, "op-1")).willReturn(
                new Result(Outcome.RESET, ACCOUNT, Instant.parse("2026-10-09T03:00:00Z"), true));

        mockMvc.perform(post(PATH)
                        .header("Idempotency-Key", "idem-1")
                        .header("X-Operator-ID", "op-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.resetAt").value("2026-10-09T03:00:00Z"))
                .andExpect(jsonPath("$.wasConfirmed").value(true));
    }

    @Test
    @DisplayName("NOT_ENROLLED → 404 TOTP_NOT_ENROLLED")
    void notEnrolled_404() throws Exception {
        given(useCase.reset(ACCOUNT, "op-1")).willReturn(new Result(Outcome.NOT_ENROLLED, ACCOUNT, null, false));

        mockMvc.perform(post(PATH).header("X-Operator-ID", "op-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOTP_NOT_ENROLLED"));
    }

    @Test
    @DisplayName("ACCOUNT_NOT_FOUND → 404 ACCOUNT_NOT_FOUND")
    void accountNotFound_404() throws Exception {
        given(useCase.reset(ACCOUNT, "op-1")).willReturn(new Result(Outcome.ACCOUNT_NOT_FOUND, ACCOUNT, null, false));

        mockMvc.perform(post(PATH).header("X-Operator-ID", "op-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("행 없음 + account-service 읽기 실패 → 503 SERVICE_UNAVAILABLE")
    void lookupFails_503() throws Exception {
        given(useCase.reset(ACCOUNT, "op-1"))
                .willThrow(new AccountServiceUnavailableException("down", new RuntimeException()));

        mockMvc.perform(post(PATH).header("X-Operator-ID", "op-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("X-Operator-ID 가 없으면 body operatorId 로 로그 상관")
    void operatorFromBody() throws Exception {
        given(useCase.reset(ACCOUNT, "op-1")).willReturn(
                new Result(Outcome.RESET, ACCOUNT, Instant.parse("2026-10-09T03:00:00Z"), false));

        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasConfirmed").value(false));
    }
}
