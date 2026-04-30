package com.example.auth.presentation;

import com.example.auth.application.LogoutUseCase;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogoutController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@DisplayName("LogoutController slice tests")
class LogoutControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogoutUseCase logoutUseCase;

    @Test
    @DisplayName("POST /api/auth/logout returns 204")
    void logout_validRequest_returns204() throws Exception {
        doNothing().when(logoutUseCase).execute(any());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout with X-Device-Id header returns 204")
    void logout_withDeviceId_returns204() throws Exception {
        doNothing().when(logoutUseCase).execute(any());

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Device-Id", "device-abc")
                        .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout with missing refreshToken returns 400")
    void logout_missingRefreshToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/logout with blank refreshToken returns 400")
    void logout_blankRefreshToken_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
