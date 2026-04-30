package com.example.auth.presentation;

import com.example.auth.application.RefreshTokenUseCase;
import com.example.auth.application.exception.SessionRevokedException;
import com.example.auth.application.exception.TokenExpiredException;
import com.example.auth.application.exception.TokenReuseDetectedException;
import com.example.auth.application.result.RefreshTokenResult;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RefreshController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
class RefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @Test
    @DisplayName("POST /api/auth/refresh returns 200 with new token pair")
    void refreshSuccess() throws Exception {
        when(refreshTokenUseCase.execute(any())).thenReturn(
                RefreshTokenResult.of("new-access", "new-refresh", 1800)
        );

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 401 for expired token")
    void refreshExpiredToken() throws Exception {
        when(refreshTokenUseCase.execute(any())).thenThrow(new TokenExpiredException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"expired-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 401 for revoked session")
    void refreshRevokedSession() throws Exception {
        when(refreshTokenUseCase.execute(any())).thenThrow(new SessionRevokedException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"revoked-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 401 TOKEN_REUSE_DETECTED on reuse")
    void refreshTokenReuseDetected() throws Exception {
        when(refreshTokenUseCase.execute(any())).thenThrow(new TokenReuseDetectedException());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"reused-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 400 for missing token")
    void refreshMissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
