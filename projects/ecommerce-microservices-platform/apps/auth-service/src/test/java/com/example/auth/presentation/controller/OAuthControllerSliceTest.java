package com.example.auth.presentation.controller;

import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.OAuthCallbackResult;
import com.example.auth.application.exception.OAuthException;
import com.example.auth.application.exception.OAuthUpstreamException;
import com.example.auth.application.service.OAuthService;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import com.example.auth.infrastructure.security.AuthRateLimitFilter;
import com.example.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.example.auth.infrastructure.security.JwtAuthenticationFilter;
import com.example.auth.infrastructure.security.JwtTokenParser;
import com.example.auth.presentation.advice.GlobalExceptionHandler;
import com.example.auth.presentation.support.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OAuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class,
    JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("OAuthController 슬라이스 테스트")
class OAuthControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthService oauthService;

    @MockitoBean
    private JwtTokenParser jwtTokenParser;

    @MockitoBean
    private AccessTokenBlocklist accessTokenBlocklist;

    @MockitoBean
    private RateLimiter rateLimiter;

    @MockitoBean
    private AuthMetrics authMetrics;

    // ── Google OAuth ──

    @Test
    @DisplayName("GET /api/auth/oauth/google - 허용된 callbackUrl이면 Google 인증 URL로 302 리다이렉트한다")
    void initiateGoogleLogin_validCallbackUrl_redirectsToGoogle() throws Exception {
        String callbackUrl = "http://localhost:3000/oauth/callback";
        given(oauthService.buildAuthorizationUrl("google", callbackUrl))
            .willReturn("https://accounts.google.com/o/oauth2/v2/auth?state=abc");

        mockMvc.perform(get("/api/auth/oauth/google").param("callbackUrl", callbackUrl))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://accounts.google.com/o/oauth2/v2/auth?state=abc"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google - 허용 목록에 없는 callbackUrl이면 400 INVALID_STATE를 반환한다")
    void initiateGoogleLogin_invalidCallbackUrl_returns400() throws Exception {
        given(oauthService.buildAuthorizationUrl(eq("google"), eq("http://evil.com/callback")))
            .willThrow(new OAuthException("Invalid callbackUrl"));

        mockMvc.perform(get("/api/auth/oauth/google").param("callbackUrl", "http://evil.com/callback"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google - callbackUrl 파라미터 누락 시 400을 반환한다")
    void initiateGoogleLogin_missingCallbackUrl_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - 성공 시 토큰을 포함한 callbackUrl로 302 리다이렉트한다")
    void handleGoogleCallback_success_redirectsWithTokens() throws Exception {
        LoginResult loginResult = new LoginResult("access-token", "refresh-token", 3600L);
        OAuthCallbackResult callbackResult = OAuthCallbackResult.success("http://localhost:3000/oauth/callback", loginResult);
        given(oauthService.handleCallback(eq("google"), any())).willReturn(callbackResult);

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("code", "auth-code")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?accessToken=access-token&refreshToken=refresh-token"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - 실패 시 error=oauth_failed를 포함한 callbackUrl로 302 리다이렉트한다")
    void handleGoogleCallback_failure_redirectsWithError() throws Exception {
        OAuthCallbackResult callbackResult = OAuthCallbackResult.failure("http://localhost:3000/oauth/callback");
        given(oauthService.handleCallback(eq("google"), any())).willReturn(callbackResult);

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("code", "bad-code")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?error=oauth_failed"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - error 파라미터가 있고 state로 callbackUrl을 복원할 수 있으면 redirect한다")
    void handleGoogleCallback_errorWithValidState_redirectsWithError() throws Exception {
        given(oauthService.resolveCallbackUrl("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("error", "access_denied")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?error=oauth_failed"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - error 파라미터가 있고 state가 없으면 400을 반환한다")
    void handleGoogleCallback_errorWithoutState_returns400() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("error", "access_denied"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - error 파라미터가 있고 state가 만료되어 callbackUrl 복원 불가하면 400을 반환한다")
    void handleGoogleCallback_errorWithExpiredState_returns400() throws Exception {
        given(oauthService.resolveCallbackUrl("expired-state"))
            .willReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("error", "access_denied")
                .param("state", "expired-state"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - OAuthException 발생 시 400 INVALID_STATE를 반환한다")
    void handleGoogleCallback_oauthException_returns400() throws Exception {
        given(oauthService.handleCallback(eq("google"), any()))
            .willThrow(new OAuthException("Invalid or expired OAuth state"));

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("code", "auth-code")
                .param("state", "expired-state"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - OAuthUpstreamException 발생 시 callbackUrl이 있으면 error=oauth_failed로 302 리다이렉트한다")
    void handleGoogleCallback_upstreamError_redirectsWithError() throws Exception {
        given(oauthService.handleCallback(eq("google"), any()))
            .willThrow(new OAuthUpstreamException("Google API call failed",
                "http://localhost:3000/oauth/callback", new RuntimeException("upstream error")));

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("code", "auth-code")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?error=oauth_failed"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/google/callback - OAuthUpstreamException 발생 시 callbackUrl이 없으면 502를 반환한다")
    void handleGoogleCallback_upstreamErrorNoCallbackUrl_returns502() throws Exception {
        given(oauthService.handleCallback(eq("google"), any()))
            .willThrow(new OAuthUpstreamException("Google API call failed",
                null, new RuntimeException("upstream error")));

        mockMvc.perform(get("/api/auth/oauth/google/callback")
                .param("code", "auth-code")
                .param("state", "valid-state"))
            .andExpect(status().isBadGateway());
    }

    // ── Naver OAuth ──

    @Test
    @DisplayName("GET /api/auth/oauth/naver - 허용된 callbackUrl이면 네이버 인증 URL로 302 리다이렉트한다")
    void initiateNaverLogin_validCallbackUrl_redirectsToNaver() throws Exception {
        String callbackUrl = "http://localhost:3000/oauth/callback";
        given(oauthService.buildAuthorizationUrl("naver", callbackUrl))
            .willReturn("https://nid.naver.com/oauth2.0/authorize?state=abc");

        mockMvc.perform(get("/api/auth/oauth/naver").param("callbackUrl", callbackUrl))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://nid.naver.com/oauth2.0/authorize?state=abc"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/naver/callback - 성공 시 토큰을 포함한 callbackUrl로 302 리다이렉트한다")
    void handleNaverCallback_success_redirectsWithTokens() throws Exception {
        LoginResult loginResult = new LoginResult("access-token", "refresh-token", 3600L);
        OAuthCallbackResult callbackResult = OAuthCallbackResult.success("http://localhost:3000/oauth/callback", loginResult);
        given(oauthService.handleCallback(eq("naver"), any())).willReturn(callbackResult);

        mockMvc.perform(get("/api/auth/oauth/naver/callback")
                .param("code", "auth-code")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?accessToken=access-token&refreshToken=refresh-token"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/naver/callback - 실패 시 error=oauth_failed를 포함한 callbackUrl로 302 리다이렉트한다")
    void handleNaverCallback_failure_redirectsWithError() throws Exception {
        OAuthCallbackResult callbackResult = OAuthCallbackResult.failure("http://localhost:3000/oauth/callback");
        given(oauthService.handleCallback(eq("naver"), any())).willReturn(callbackResult);

        mockMvc.perform(get("/api/auth/oauth/naver/callback")
                .param("code", "bad-code")
                .param("state", "valid-state"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location",
                "http://localhost:3000/oauth/callback?error=oauth_failed"));
    }

    // ── 지원하지 않는 provider ──

    @Test
    @DisplayName("GET /api/auth/oauth/kakao - 지원하지 않는 provider면 400을 반환한다")
    void initiateUnsupportedProvider_returns400() throws Exception {
        given(oauthService.buildAuthorizationUrl(eq("kakao"), any()))
            .willThrow(new OAuthException("Unsupported OAuth provider: kakao"));

        mockMvc.perform(get("/api/auth/oauth/kakao").param("callbackUrl", "http://localhost:3000/oauth/callback"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
