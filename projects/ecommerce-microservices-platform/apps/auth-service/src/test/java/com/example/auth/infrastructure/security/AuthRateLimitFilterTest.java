package com.example.auth.infrastructure.security;

import com.example.auth.application.service.LoginService;
import com.example.auth.application.service.LogoutService;
import com.example.auth.application.service.RefreshTokenService;
import com.example.auth.application.service.SignupService;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.auth.presentation.support.ClientIpResolver;
import com.example.auth.presentation.advice.GlobalExceptionHandler;
import com.example.auth.presentation.controller.AuthController;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("AuthRateLimitFilter 슬라이스 테스트")
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RateLimiter loginRateLimiter;

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
    private AuthMetricsRecorder authMetrics;

    @Test
    @DisplayName("rate limit 초과 시 /api/auth/login 에 429 반환")
    void whenRateLimitExceeded_login_returns429() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(true);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("rate limit 초과 시 /api/auth/signup 에 429 반환")
    void whenRateLimitExceeded_signup_returns429() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(true);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!",
                    "name", "테스터"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("rate limit 초과 시 /api/auth/refresh 에 429 반환")
    void whenRateLimitExceeded_refresh_returns429() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(true);

        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", "token"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("rate limit 미초과 시 요청이 정상 처리된다")
    void whenRateLimitNotExceeded_requestPasses() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(false);
        given(loginService.login(any())).willThrow(new com.example.auth.application.exception.InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("X-Forwarded-For가 있으면 첫 번째 IP가 rate limit 키에 사용된다")
    void xffHeader_firstIpUsedAsClientIp() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(false);
        given(loginService.login(any())).willThrow(new com.example.auth.application.exception.InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .header("X-Forwarded-For", "1.2.3.4, 5.6.7.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isUnauthorized());

        verify(loginRateLimiter).isRateLimited(eq("1.2.3.4:/api/auth/login"), anyInt(), anyLong());
    }

    @Test
    @DisplayName("X-Forwarded-For가 없으면 remoteAddr가 rate limit 키에 사용된다")
    void noXffHeader_remoteAddrUsedAsClientIp() throws Exception {
        given(loginRateLimiter.isRateLimited(any(), anyInt(), anyLong())).willReturn(false);
        given(loginService.login(any())).willThrow(new com.example.auth.application.exception.InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isUnauthorized());

        verify(loginRateLimiter).isRateLimited(eq("127.0.0.1:/api/auth/login"), anyInt(), anyLong());
    }
}
