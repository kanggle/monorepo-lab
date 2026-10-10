package com.example.auth.presentation;

import com.example.auth.application.port.ConsumerPoolSignupPort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.Preview;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewOutcome;
import com.example.auth.application.port.OperatorInvitationAcceptancePort.PreviewResult;
import com.example.auth.infrastructure.config.WebLoginSecurityConfig;
import com.example.auth.infrastructure.security.CredentialAuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S3 — the REAL {@code WebLoginSecurityConfig} chain on the four operator-invitation routes: an
 * anonymous GET renders (no entry-point redirect — the page parks itself as the continuation instead), and a POST
 * without CSRF is refused before the controller (no admin / account call).
 */
@WebMvcTest(OperatorInvitationPageController.class)
@Import(WebLoginSecurityConfig.class)
@DisplayName("운영자 초대 화면 보안 체인 (TASK-MONO-772 S3)")
class OperatorInvitationPageSecurityChainSliceTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CredentialAuthenticationProvider credentialAuthenticationProvider;
    @MockitoBean private OperatorInvitationAcceptancePort acceptancePort;
    @MockitoBean private ConsumerPoolSignupPort signupPort;

    @Test
    @DisplayName("비로그인 GET 수락 화면 · 가입 화면 → 200 (로그인으로 튕기지 않는다)")
    void anonymousGets_render() throws Exception {
        given(acceptancePort.preview("tok")).willReturn(new PreviewResult(PreviewOutcome.FOUND, new Preview(
                "acme-corp", "Acme", "a*****@example.com", List.of("SUPPORT_LOCK"), "PENDING", false, Instant.now())));

        mockMvc.perform(get("/operator-invitations/accept").param("token", "tok")).andExpect(status().isOk());
        mockMvc.perform(get("/operator-invitations/signup").param("token", "tok")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("🔴 CSRF 없는 POST 수락 · 가입 → 403 · 수락 · 가입 호출 없음")
    void postsWithoutCsrf_forbidden() throws Exception {
        mockMvc.perform(post("/operator-invitations/accept").param("token", "tok")).andExpect(status().isForbidden());
        mockMvc.perform(post("/operator-invitations/signup").param("token", "tok")
                        .param("email", "a@example.com").param("password", "Password1!")
                        .param("confirmPassword", "Password1!"))
                .andExpect(status().isForbidden());

        verify(acceptancePort, never()).accept(anyString(), anyString());
        verifyNoInteractions(signupPort);
    }

    @Test
    @DisplayName("CSRF 있는 비로그인 POST 수락 → 체인 통과 · 컨트롤러가 401 «로그인» (admin 호출 없음)")
    void anonymousPostWithCsrf_reachesController() throws Exception {
        given(acceptancePort.preview("tok")).willReturn(PreviewResult.notFound());

        mockMvc.perform(post("/operator-invitations/accept").param("token", "tok").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(acceptancePort, never()).accept(anyString(), anyString());
    }
}
