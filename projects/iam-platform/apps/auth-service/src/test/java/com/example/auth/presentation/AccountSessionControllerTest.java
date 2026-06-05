package com.example.auth.presentation;

import com.example.auth.application.GetCurrentSessionUseCase;
import com.example.auth.application.ListSessionsUseCase;
import com.example.auth.application.RevokeAllOtherSessionsUseCase;
import com.example.auth.application.RevokeSessionUseCase;
import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.exception.SessionOwnershipMismatchException;
import com.example.auth.application.result.DeviceSessionResult;
import com.example.auth.application.result.ListSessionsResult;
import com.example.auth.application.result.RevokeOthersResult;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountSessionController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class AccountSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListSessionsUseCase listSessionsUseCase;
    @MockitoBean
    private GetCurrentSessionUseCase getCurrentSessionUseCase;
    @MockitoBean
    private RevokeSessionUseCase revokeSessionUseCase;
    @MockitoBean
    private RevokeAllOtherSessionsUseCase revokeAllOtherSessionsUseCase;

    @Test
    @DisplayName("GET /api/accounts/me/sessions returns mapped list with maxActiveSessions")
    void list() throws Exception {
        DeviceSessionResult item = new DeviceSessionResult(
                "dev-1", "Chrome", "192.168.*.*", "KR",
                Instant.parse("2026-04-01T10:00:00Z"),
                Instant.parse("2026-04-13T08:22:00Z"),
                true);
        when(listSessionsUseCase.execute("acc-1", "dev-1"))
                .thenReturn(new ListSessionsResult(List.of(item), 1, 10));

        mockMvc.perform(get("/api/accounts/me/sessions")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.maxActiveSessions").value(10))
                .andExpect(jsonPath("$.items[0].deviceId").value("dev-1"))
                .andExpect(jsonPath("$.items[0].ipMasked").value("192.168.*.*"))
                .andExpect(jsonPath("$.items[0].current").value(true));
    }

    @Test
    @DisplayName("GET /current returns the resolved session")
    void getCurrent() throws Exception {
        when(getCurrentSessionUseCase.execute("acc-1", "dev-1"))
                .thenReturn(new DeviceSessionResult(
                        "dev-1", "Chrome", "192.168.*.*", "KR",
                        Instant.parse("2026-04-01T10:00:00Z"),
                        Instant.parse("2026-04-13T08:22:00Z"),
                        true));

        mockMvc.perform(get("/api/accounts/me/sessions/current")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("dev-1"))
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    @DisplayName("GET /current returns 404 SESSION_NOT_FOUND when use-case throws")
    void getCurrentNotFound() throws Exception {
        when(getCurrentSessionUseCase.execute(anyString(), any()))
                .thenThrow(new SessionNotFoundException());

        mockMvc.perform(get("/api/accounts/me/sessions/current")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Device-Id", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /{deviceId} returns 204 on success")
    void revokeOne() throws Exception {
        mockMvc.perform(delete("/api/accounts/me/sessions/dev-2")
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isNoContent());

        verify(revokeSessionUseCase).execute("acc-1", "dev-2");
    }

    @Test
    @DisplayName("DELETE /{deviceId} returns 403 on ownership mismatch")
    void revokeOneOwnership() throws Exception {
        doThrow(new SessionOwnershipMismatchException())
                .when(revokeSessionUseCase).execute(eq("acc-1"), eq("dev-x"));

        mockMvc.perform(delete("/api/accounts/me/sessions/dev-x")
                        .header("X-Account-Id", "acc-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION_OWNERSHIP_MISMATCH"));
    }

    @Test
    @DisplayName("DELETE bulk returns the revoked count")
    void revokeOthers() throws Exception {
        when(revokeAllOtherSessionsUseCase.execute("acc-1", "dev-1"))
                .thenReturn(new RevokeOthersResult(3));

        mockMvc.perform(delete("/api/accounts/me/sessions")
                        .header("X-Account-Id", "acc-1")
                        .header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(3));
    }

    @Test
    @DisplayName("Missing X-Account-Id returns 400 VALIDATION_ERROR")
    void missingAccountIdHeader() throws Exception {
        mockMvc.perform(get("/api/accounts/me/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
