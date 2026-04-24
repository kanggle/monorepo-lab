package com.example.auth.presentation.controller;

import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.SignupResult;
import com.example.auth.application.exception.EmailAlreadyExistsException;
import com.example.auth.application.exception.InvalidCredentialsException;
import com.example.auth.application.service.LoginService;
import com.example.auth.application.service.LogoutService;
import com.example.auth.application.service.RefreshTokenService;
import com.example.auth.application.service.SignupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.security.JwtAuthenticationFilter;
import com.example.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.example.auth.infrastructure.security.JwtTokenParser;
import com.example.auth.infrastructure.security.AuthRateLimitFilter;
import com.example.auth.presentation.support.ClientIpResolver;
import com.example.auth.presentation.advice.GlobalExceptionHandler;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class, JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("AuthController 슬라이스 테스트")
class AuthControllerTest {

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
    @DisplayName("POST /api/auth/signup - 201 반환")
    void signup_success() throws Exception {
        SignupResult result = new SignupResult(UUID.randomUUID(), "test@example.com", "홍길동", Instant.now());
        given(signupService.signup(any())).willReturn(result);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!",
                    "name", "홍길동"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email").value("test@example.com"))
            .andExpect(jsonPath("$.name").value("홍길동"))
            .andExpect(jsonPath("$.userId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/signup - createdAt이 ISO 8601 UTC 형식(Z 접미사)이다")
    void signup_createdAt_iso8601utc() throws Exception {
        SignupResult result = new SignupResult(UUID.randomUUID(), "test@example.com", "홍길동", Instant.parse("2026-03-20T10:30:00Z"));
        given(signupService.signup(any())).willReturn(result);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!",
                    "name", "홍길동"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.createdAt").value("2026-03-20T10:30:00Z"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 필드 누락 시 400")
    void signup_missingField_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com"
                    // password, name 누락
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 깨진 JSON 본문 시 400 / VALIDATION_ERROR")
    void signup_malformedBody_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("Malformed request body"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호 강도 미달 시 400")
    void signup_weakPassword_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "onlyletters",
                    "name", "홍길동"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 중복 이메일 시 409")
    void signup_duplicateEmail_409() throws Exception {
        given(signupService.signup(any())).willThrow(new EmailAlreadyExistsException());

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!",
                    "name", "홍길동"
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 200 반환, 토큰 포함")
    void login_success() throws Exception {
        LoginResult result = new LoginResult("jwt-access-token", "refresh-uuid", 3600L);
        given(loginService.login(any())).willReturn(result);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("jwt-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-uuid"))
            .andExpect(jsonPath("$.expiresIn").value(3600));
    }

    @Test
    @DisplayName("POST /api/auth/login - 비밀번호 128자 초과 시 400")
    void login_tooLongPassword_400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "a".repeat(129)
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 이메일 255자 초과 시 400")
    void login_tooLongEmail_400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "a".repeat(248) + "@a.com",
                    "password", "password1!"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호 128자는 정상")
    void signup_maxLengthPassword_success() throws Exception {
        SignupResult result = new SignupResult(UUID.randomUUID(), "test@example.com", "홍길동", Instant.now());
        given(signupService.signup(any())).willReturn(result);

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "A1!" + "a".repeat(125),
                    "name", "홍길동"
                ))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 비밀번호 129자 초과 시 400")
    void signup_tooLongPassword_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "a1" + "a".repeat(127),
                    "name", "홍길동"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일 255자 초과 시 400")
    void signup_tooLongEmail_400() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "a".repeat(248) + "@a.com",
                    "password", "password1!",
                    "name", "홍길동"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 잘못된 자격증명 시 401")
    void login_invalidCredentials_401() throws Exception {
        given(loginService.login(any())).willThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "test@example.com",
                    "password", "wrongpw1!"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
