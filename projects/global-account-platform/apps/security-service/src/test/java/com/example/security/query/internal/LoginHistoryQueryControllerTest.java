package com.example.security.query.internal;

import com.example.security.infrastructure.config.InternalAuthFilter;
import com.example.security.query.SecurityQueryService;
import com.example.security.query.dto.LoginHistoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = LoginHistoryQueryController.class)
@Import({QueryExceptionHandler.class, InternalAuthFilter.class})
@ActiveProfiles("test")
class LoginHistoryQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityQueryService queryService;

    private static final String TOKEN = "test-internal-token";

    @Test
    @DisplayName("Returns 400 VALIDATION_ERROR when accountId is missing")
    void missingAccountIdReturns400() throws Exception {
        mockMvc.perform(get("/internal/security/login-history")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required parameter: accountId"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns 403 PERMISSION_DENIED when X-Internal-Token is missing")
    void missingTokenReturns403() throws Exception {
        mockMvc.perform(get("/internal/security/login-history")
                        .param("accountId", "acc-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns 403 PERMISSION_DENIED when X-Internal-Token is invalid")
    void invalidTokenReturns403() throws Exception {
        mockMvc.perform(get("/internal/security/login-history")
                        .header("X-Internal-Token", "wrong-token")
                        .param("accountId", "acc-001"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("서비스가 IllegalArgumentException을 던지면 400 VALIDATION_ERROR 응답을 반환한다")
    void getLoginHistory_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(queryService.findLoginHistory(eq("acc-001"), isNull(), isNull(), isNull(), any()))
                .thenThrow(new IllegalArgumentException("from must be before to"));

        mockMvc.perform(get("/internal/security/login-history")
                        .header("X-Internal-Token", TOKEN)
                        .param("accountId", "acc-001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("from must be before to"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("Returns 200 with paginated login history when authenticated")
    void authenticatedRequestReturns200() throws Exception {
        LoginHistoryView view = new LoginHistoryView(
                "evt-001", "acc-001", "SUCCESS",
                "192.168.1.***", "Chrome 120", "abcdef123456",
                "KR", Instant.parse("2026-04-12T10:00:00Z"));

        when(queryService.findLoginHistory(eq("acc-001"), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/internal/security/login-history")
                        .header("X-Internal-Token", TOKEN)
                        .param("accountId", "acc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("evt-001"))
                .andExpect(jsonPath("$.content[0].ipMasked").value("192.168.1.***"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
