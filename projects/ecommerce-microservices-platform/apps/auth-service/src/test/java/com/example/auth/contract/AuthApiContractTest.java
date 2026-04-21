package com.example.auth.contract;

import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.RefreshResult;
import com.example.auth.application.dto.SignupResult;
import com.example.auth.application.exception.InvalidCredentialsException;
import com.example.auth.application.service.LoginService;
import com.example.auth.application.service.LogoutService;
import com.example.auth.application.service.RefreshTokenService;
import com.example.auth.application.service.SignupService;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import com.example.auth.infrastructure.security.JwtAuthenticationFilter;
import com.example.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.example.auth.infrastructure.security.JwtTokenParser;
import com.example.auth.infrastructure.security.AuthRateLimitFilter;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.auth.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * auth-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/auth-api.md
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class,
        JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("Auth API 컨트랙트 테스트 — specs/contracts/http/auth-api.md")
class AuthApiContractTest {

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

    private static final String SPEC_REF = "specs/contracts/http/auth-api.md";

    // ─── POST /api/auth/signup — 201 ────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/signup 응답은 {userId, email, name, createdAt}만 포함한다")
    void signup_response_containsSpecFields() throws Exception {
        given(signupService.signup(any()))
                .willReturn(new SignupResult(UUID.randomUUID(), "test@example.com", "홍길동", Instant.now()));

        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test@example.com",
                                "password", "password1!",
                                "name", "홍길동"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("userId", "email", "name", "createdAt"),
                SPEC_REF + " POST /api/auth/signup 201");
    }

    // ─── POST /api/auth/login — 200 ─────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login 응답은 {accessToken, refreshToken, expiresIn}만 포함한다")
    void login_response_containsSpecFields() throws Exception {
        given(loginService.login(any()))
                .willReturn(new LoginResult("jwt-token", "refresh-token", 3600L));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "test@example.com",
                                "password", "password1!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("accessToken", "refreshToken", "expiresIn"),
                SPEC_REF + " POST /api/auth/login 200");
    }

    // ─── POST /api/auth/refresh — 200 ───────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/refresh 응답은 {accessToken, refreshToken, expiresIn}만 포함한다")
    void refresh_response_containsSpecFields() throws Exception {
        given(refreshTokenService.refresh(any()))
                .willReturn(new RefreshResult("new-jwt", "new-refresh", 3600L));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "refreshToken", "some-token"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("accessToken", "refreshToken", "expiresIn"),
                SPEC_REF + " POST /api/auth/refresh 200");
    }

    // ─── POST /api/auth/logout — 204 ────────────────────────────────────
    // logout returns 204 No Content (empty body), no field verification needed

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "",
                                "password", "",
                                "name", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                SPEC_REF + " error format");
    }
}
