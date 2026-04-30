package com.example.auth.presentation;

import com.example.auth.application.ChangePasswordUseCase;
import com.example.auth.application.command.ChangePasswordCommand;
import com.example.auth.application.exception.CurrentPasswordMismatchException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@DisplayName("PasswordController slice tests")
class PasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChangePasswordUseCase changePasswordUseCase;

    @Test
    @DisplayName("PATCH /api/auth/password returns 204 on valid request and forwards X-Account-Id")
    void changePassword_returns204() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPassw0rd!",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isNoContent());

        ArgumentCaptor<ChangePasswordCommand> captor =
                ArgumentCaptor.forClass(ChangePasswordCommand.class);
        verify(changePasswordUseCase).execute(captor.capture());
        ChangePasswordCommand cmd = captor.getValue();
        assertThat(cmd.accountId()).isEqualTo("acc-1");
        assertThat(cmd.currentPassword()).isEqualTo("OldPassw0rd!");
        assertThat(cmd.newPassword()).isEqualTo("NewPassw0rd!");
    }

    @Test
    @DisplayName("PATCH /api/auth/password returns 400 CREDENTIALS_INVALID when current password mismatches")
    void changePassword_currentPasswordMismatch_returns400() throws Exception {
        doThrow(new CurrentPasswordMismatchException())
                .when(changePasswordUseCase).execute(any(ChangePasswordCommand.class));

        mockMvc.perform(patch("/api/auth/password")
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "WrongPassw0rd!",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_INVALID"));
    }

    @Test
    @DisplayName("PATCH /api/auth/password returns 400 PASSWORD_POLICY_VIOLATION when new password fails policy")
    void changePassword_policyViolation_returns400() throws Exception {
        doThrow(new PasswordPolicyViolationException("Password must be at least 8 characters"))
                .when(changePasswordUseCase).execute(any(ChangePasswordCommand.class));

        mockMvc.perform(patch("/api/auth/password")
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPassw0rd!",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("PATCH /api/auth/password without X-Account-Id header returns 400 VALIDATION_ERROR")
    void changePassword_missingAccountIdHeader_returns400() throws Exception {
        // The gateway is the auth boundary for this service: when the gateway
        // rejects an unauthenticated request it never forwards X-Account-Id.
        // Service-side, the missing required header surfaces as 400
        // VALIDATION_ERROR (matches AccountSessionController contract).
        mockMvc.perform(patch("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "OldPassw0rd!",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH /api/auth/password with blank currentPassword returns 400 VALIDATION_ERROR")
    void changePassword_blankCurrentPassword_returns400() throws Exception {
        mockMvc.perform(patch("/api/auth/password")
                        .header("X-Account-Id", "acc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "",
                                  "newPassword": "NewPassw0rd!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
