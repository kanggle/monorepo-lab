package com.example.auth.presentation;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.RequestPasswordResetUseCase;
import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.command.RequestPasswordResetCommand;
import com.example.auth.application.exception.PasswordResetTokenInvalidException;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordResetController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@DisplayName("PasswordResetController 슬라이스 테스트")
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @Test
    @DisplayName("등록된 이메일 요청은 204를 반환한다")
    void requestReset_existingEmail_returns204() throws Exception {
        // The use case is a void method; doNothing keeps the test explicit
        // even though Mockito would default to that behaviour.
        doNothing().when(requestPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(RequestPasswordResetCommand.class));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isNoContent());

        ArgumentCaptor<RequestPasswordResetCommand> captor =
                ArgumentCaptor.forClass(RequestPasswordResetCommand.class);
        verify(requestPasswordResetUseCase).execute(captor.capture());
        assertThat(captor.getValue().email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("미등록 이메일 요청도 204를 반환한다 (계정 존재 여부 유출 방지)")
    void requestReset_unknownEmail_returns204() throws Exception {
        // Use case treats unknown emails as silent no-ops (verified separately
        // in RequestPasswordResetUseCaseTest). The controller must still 204.
        doNothing().when(requestPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(RequestPasswordResetCommand.class));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ghost@example.com"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(requestPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(RequestPasswordResetCommand.class));
    }

    @Test
    @DisplayName("이메일 누락은 400 VALIDATION_ERROR")
    void requestReset_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("이메일 형식 오류는 400 VALIDATION_ERROR")
    void requestReset_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ----------------------------------------------------------------------
    // POST /api/auth/password-reset/confirm — TASK-BE-109
    // ----------------------------------------------------------------------

    @Test
    @DisplayName("confirmReset_validRequest_returns204 — token + newPassword가 use case에 그대로 전달된다")
    void confirmReset_validRequest_returns204() throws Exception {
        doNothing().when(confirmPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(ConfirmPasswordResetCommand.class));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token-uuid",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ConfirmPasswordResetCommand> captor =
                ArgumentCaptor.forClass(ConfirmPasswordResetCommand.class);
        verify(confirmPasswordResetUseCase).execute(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("reset-token-uuid");
        assertThat(captor.getValue().newPassword()).isEqualTo("NewPassw0rd!");
    }

    @Test
    @DisplayName("confirmReset_invalidToken_returns400 — PasswordResetTokenInvalidException → PASSWORD_RESET_TOKEN_INVALID")
    void confirmReset_invalidToken_returns400() throws Exception {
        doThrow(new PasswordResetTokenInvalidException())
                .when(confirmPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(ConfirmPasswordResetCommand.class));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-or-unknown",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("confirmReset_policyViolation_returns400 — PasswordPolicyViolationException → PASSWORD_POLICY_VIOLATION")
    void confirmReset_policyViolation_returns400() throws Exception {
        doThrow(new PasswordPolicyViolationException("Password must be at least 8 characters"))
                .when(confirmPasswordResetUseCase)
                .execute(org.mockito.ArgumentMatchers.any(ConfirmPasswordResetCommand.class));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "reset-token-uuid",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("confirmReset_missingToken_returns400 — @NotBlank token validation")
    void confirmReset_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
