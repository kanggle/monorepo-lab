package com.example.auth.presentation;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.RequestPasswordResetUseCase;
import com.example.auth.infrastructure.config.WebLoginSecurityConfig;
import com.example.auth.infrastructure.security.CredentialAuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-627 AC-4 — exercises the REAL {@code WebLoginSecurityConfig} filter chain (not a
 * standalone MockMvc setup, unlike {@link PasswordResetPageSliceTest}) against the two
 * password-reset page routes: a CSRF-less POST must be rejected, and an anonymous GET must
 * render directly (200) rather than being redirected to {@code /login} by an authentication
 * entry point — proving both routes are on the {@code permitAll} {@code @Order(0)} chain and not
 * accidentally falling through to a chain that demands authentication.
 *
 * <p>No Testcontainers / database is needed: {@link WebLoginSecurityConfig} only needs a
 * {@link CredentialAuthenticationProvider} bean to build its {@code AuthenticationManager}, and
 * that bean is never invoked by anonymous GET/POST on these two routes, so a {@code @MockitoBean}
 * is enough (no DB-backed collaborators of its own need to be real).
 */
@WebMvcTest(PasswordResetPageController.class)
@Import(WebLoginSecurityConfig.class)
@DisplayName("비밀번호 재설정 화면 보안 체인 (TASK-BE-627 AC-4)")
class PasswordResetPageSecurityChainSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CredentialAuthenticationProvider credentialAuthenticationProvider;

    @MockitoBean
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @MockitoBean
    private ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    @Test
    @DisplayName("비로그인 GET /password-reset/request → 200 (로그인으로 튕기지 않는다)")
    void anonymousGetRequestPage_ok() throws Exception {
        mockMvc.perform(get("/password-reset/request")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인 GET /password-reset → 200 (로그인으로 튕기지 않는다)")
    void anonymousGetConfirmPage_ok() throws Exception {
        mockMvc.perform(get("/password-reset")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 AC-4: CSRF 토큰 없는 POST /password-reset/request → 403, 유스케이스 호출 없음")
    void postRequestWithoutCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/password-reset/request").param("email", "user@example.com"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(requestPasswordResetUseCase);
    }

    @Test
    @DisplayName("🔴 AC-4: CSRF 토큰 없는 POST /password-reset → 403, 유스케이스 호출 없음")
    void postConfirmWithoutCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/password-reset")
                        .param("token", "some-token")
                        .param("newPassword", "NewPassw0rd!")
                        .param("confirmPassword", "NewPassw0rd!"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(confirmPasswordResetUseCase);
    }

    @Test
    @DisplayName("CSRF 토큰 있는 POST /password-reset/request → permitAll 통과(200) — 401/403 아님")
    void postRequestWithCsrf_passesSecurityChain() throws Exception {
        mockMvc.perform(post("/password-reset/request").param("email", "user@example.com").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CSRF 토큰 있는 POST /password-reset → permitAll 통과(토큰 없으면 요청 화면 안내 200) — 401/403 아님")
    void postConfirmWithCsrf_passesSecurityChain() throws Exception {
        mockMvc.perform(post("/password-reset")
                        .param("newPassword", "NewPassw0rd!").param("confirmPassword", "NewPassw0rd!")
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}
