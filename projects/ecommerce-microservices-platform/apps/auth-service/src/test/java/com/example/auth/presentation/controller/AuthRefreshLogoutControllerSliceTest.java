package com.example.auth.presentation.controller;

import com.example.auth.application.dto.RefreshResult;
import com.example.auth.application.exception.InvalidRefreshTokenException;
import com.example.auth.application.exception.RefreshTokenRevokedException;
import com.example.auth.application.service.LoginService;
import com.example.auth.application.service.LogoutService;
import com.example.auth.application.service.RefreshTokenService;
import com.example.auth.application.service.SignupService;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.security.JwtAuthenticationFilter;
import com.example.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.example.auth.domain.service.ParsedToken;
import com.example.auth.infrastructure.security.JwtTokenParser;
import com.example.auth.infrastructure.security.AuthRateLimitFilter;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import com.example.auth.presentation.support.ClientIpResolver;
import com.example.auth.presentation.advice.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("AuthController refresh/logout 슬라이스 테스트")
class AuthRefreshLogoutControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SignupService signupService;

    @MockitoBean
    private LoginService loginService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private LogoutService logoutService;

    @MockitoBean
    private JwtTokenParser jwtTokenParser;

    @MockitoBean
    private AccessTokenBlocklist accessTokenBlocklist;

    @MockitoBean
    private RateLimiter loginRateLimiter;

    @MockitoBean
    private AuthMetrics authMetrics;

    @Test
    @DisplayName("POST /api/auth/refresh - 유효한 토큰으로 200 반환 (rotation)")
    void refresh_success() throws Exception {
        given(refreshTokenService.refresh(any())).willReturn(new RefreshResult("new-jwt", "new-refresh", 3600L));

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "valid-token"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-jwt"))
            .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
            .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 존재하지 않는 토큰이면 401 INVALID_REFRESH_TOKEN")
    void refresh_invalidToken_401() throws Exception {
        given(refreshTokenService.refresh(any())).willThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bad-token-too-short-not"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 폐기된 토큰이면 401 REFRESH_TOKEN_REVOKED")
    void refresh_revokedToken_401() throws Exception {
        given(refreshTokenService.refresh(any())).willThrow(new RefreshTokenRevokedException());

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "revoked-token"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REVOKED"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - 필드 누락 시 400")
    void refresh_missingField_400() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - 유효한 JWT Bearer로 204 반환")
    void logout_success() throws Exception {
        willDoNothing().given(logoutService).logout(any());
        given(jwtTokenParser.parse(any())).willReturn(new ParsedToken(UUID.randomUUID(), "test@example.com"));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"))))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/auth/logout - JWT 없으면 401")
    void logout_noJwt_401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout - Bearer prefix 없는 Authorization 헤더면 401")
    void logout_invalidBearerHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "InvalidToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("AccessDeniedException 발생 시 403과 표준 ErrorResponse 형식 반환")
    void accessDenied_returns403WithErrorResponseFormat() throws Exception {
        given(jwtTokenParser.parse(any())).willReturn(new ParsedToken(UUID.randomUUID(), "test@example.com"));
        willThrow(new AccessDeniedException("Access denied")).given(logoutService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer valid-jwt-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "some-token"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
