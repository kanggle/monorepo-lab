package com.example.auth.presentation;

import com.example.auth.application.OAuthLoginUseCase;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.OAuthEmailRequiredException;
import com.example.auth.application.exception.UnsupportedProviderException;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.application.result.OAuthLoginResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OAuthController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@DisplayName("OAuthController slice tests")
class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthLoginUseCase oAuthLoginUseCase;

    // ── GET /api/auth/oauth/authorize ─────────────────────────────────────────

    @Test
    @DisplayName("GET /api/auth/oauth/authorize returns 200 with authorizationUrl and state")
    void authorize_validProvider_returns200() throws Exception {
        when(oAuthLoginUseCase.authorize(eq("google"), isNull()))
                .thenReturn(new OAuthAuthorizeResult("https://accounts.google.com/o/oauth2/auth?...", "state-xyz"));

        mockMvc.perform(get("/api/auth/oauth/authorize")
                        .param("provider", "google"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value("https://accounts.google.com/o/oauth2/auth?..."))
                .andExpect(jsonPath("$.state").value("state-xyz"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/authorize with optional redirectUri returns 200")
    void authorize_withRedirectUri_returns200() throws Exception {
        when(oAuthLoginUseCase.authorize(eq("kakao"), eq("https://app.example.com/callback")))
                .thenReturn(new OAuthAuthorizeResult("https://kauth.kakao.com/oauth/authorize?...", "state-abc"));

        mockMvc.perform(get("/api/auth/oauth/authorize")
                        .param("provider", "kakao")
                        .param("redirectUri", "https://app.example.com/callback"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("state-abc"));
    }

    @Test
    @DisplayName("GET /api/auth/oauth/authorize with unsupported provider returns 400")
    void authorize_unsupportedProvider_returns400() throws Exception {
        when(oAuthLoginUseCase.authorize(eq("unknown"), any()))
                .thenThrow(new UnsupportedProviderException("unknown"));

        mockMvc.perform(get("/api/auth/oauth/authorize")
                        .param("provider", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROVIDER"));
    }

    // ── POST /api/auth/oauth/callback ─────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/oauth/callback existing account returns 200")
    void callback_existingAccount_returns200() throws Exception {
        when(oAuthLoginUseCase.callback(any()))
                .thenReturn(new OAuthLoginResult("access-tok", "refresh-tok", 1800L, 604800L, false));

        mockMvc.perform(post("/api/auth/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "google",
                                  "code": "auth-code-123",
                                  "state": "state-xyz"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-tok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-tok"))
                .andExpect(jsonPath("$.isNewAccount").value(false));
    }

    @Test
    @DisplayName("POST /api/auth/oauth/callback new account returns 200 with isNewAccount=true")
    void callback_newAccount_returnsIsNewAccountTrue() throws Exception {
        when(oAuthLoginUseCase.callback(any()))
                .thenReturn(new OAuthLoginResult("access-tok", "refresh-tok", 1800L, 604800L, true));

        mockMvc.perform(post("/api/auth/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "kakao",
                                  "code": "auth-code-456",
                                  "state": "state-abc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNewAccount").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/oauth/callback with invalid state returns 401")
    void callback_invalidState_returns401() throws Exception {
        when(oAuthLoginUseCase.callback(any()))
                .thenThrow(new InvalidOAuthStateException());

        mockMvc.perform(post("/api/auth/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "google",
                                  "code": "code",
                                  "state": "tampered-state"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @DisplayName("POST /api/auth/oauth/callback without email permission returns 422")
    void callback_emailRequired_returns422() throws Exception {
        when(oAuthLoginUseCase.callback(any()))
                .thenThrow(new OAuthEmailRequiredException());

        mockMvc.perform(post("/api/auth/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "google",
                                  "code": "code",
                                  "state": "state-xyz"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMAIL_REQUIRED"));
    }

    @Test
    @DisplayName("POST /api/auth/oauth/callback missing required fields returns 400")
    void callback_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/oauth/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "google"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
