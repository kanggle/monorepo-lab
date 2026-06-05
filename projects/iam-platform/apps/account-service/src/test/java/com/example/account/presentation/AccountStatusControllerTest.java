package com.example.account.presentation;

import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountStatusController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AccountStatusController slice tests")
class AccountStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountStatusUseCase accountStatusUseCase;

    @Test
    @DisplayName("GET /api/accounts/me/status returns 200")
    void getStatus_validRequest_returns200() throws Exception {
        given(accountStatusUseCase.getStatus(eq("acc-123")))
                .willReturn(new AccountStatusResult("acc-123", "ACTIVE", Instant.now(), null));

        mockMvc.perform(get("/api/accounts/me/status")
                        .header("X-Account-Id", "acc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("DELETE /api/accounts/me returns 202")
    void deleteAccount_validRequest_returns202() throws Exception {
        Instant gracePeriodEndsAt = Instant.now().plusSeconds(86400L * 30);
        given(accountStatusUseCase.deleteAccount(eq("acc-123"), any(), any(), any()))
                .willReturn(new DeleteAccountResult("acc-123", "ACTIVE", "DELETED", gracePeriodEndsAt));

        mockMvc.perform(delete("/api/accounts/me")
                        .header("X-Account-Id", "acc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "MyPassword1!",
                                  "reason": "No longer needed"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.currentStatus").value("DELETED"))
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.gracePeriodEndsAt").exists());
    }

    @Test
    @DisplayName("DELETE /api/accounts/me already DELETED returns 409")
    void deleteAccount_alreadyDeleted_returns409() throws Exception {
        given(accountStatusUseCase.deleteAccount(eq("acc-123"), any(), any(), any()))
                .willThrow(new StateTransitionException(AccountStatus.DELETED, AccountStatus.DELETED,
                        StatusChangeReason.USER_REQUEST));

        mockMvc.perform(delete("/api/accounts/me")
                        .header("X-Account-Id", "acc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "MyPassword1!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_TRANSITION_INVALID"));
    }
}
