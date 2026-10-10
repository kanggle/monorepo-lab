package com.example.account.presentation;

import com.example.account.application.command.SendOperatorInvitationMailCommand;
import com.example.account.application.exception.AccountEmailMismatchException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.application.exception.InvitationEmailSendFailedException;
import com.example.account.application.result.VerifiedEmailMatchResult;
import com.example.account.application.service.SendOperatorInvitationMailUseCase;
import com.example.account.application.service.VerifiedEmailMatchUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import com.example.account.presentation.internal.OperatorInvitationSupportController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S2 — {@code POST /internal/accounts/{id}/verified-email:match} and
 * {@code POST /internal/notifications/operator-invitation} (admin-to-account.md): status codes per refusal, and
 * the raw token never echoed back.
 */
@WebMvcTest(OperatorInvitationSupportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("OperatorInvitationSupportController slice (TASK-MONO-772 S2)")
class OperatorInvitationSupportControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000772";
    private static final String MATCH = "/internal/accounts/{a}/verified-email:match";
    private static final String MAIL = "/internal/notifications/operator-invitation";
    private static final String MAIL_BODY = """
            {"to":"person@example.com","token":"raw-token-xyz","tenantId":"acme-corp",
             "inviterDisplayName":"김관리","expiresAt":"2026-10-17T10:00:00Z"}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VerifiedEmailMatchUseCase matchUseCase;
    @MockitoBean private SendOperatorInvitationMailUseCase mailUseCase;

    private org.springframework.test.web.servlet.ResultActions match(String email) throws Exception {
        return mockMvc.perform(post(MATCH, ACCOUNT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedEmail\":\"" + email + "\"}"));
    }

    @Test
    @DisplayName("match 200 → {accountId, emailVerifiedAt}")
    void match_200() throws Exception {
        given(matchUseCase.match(ACCOUNT, "person@example.com"))
                .willReturn(new VerifiedEmailMatchResult(ACCOUNT, Instant.parse("2026-10-09T10:00:00Z")));
        match("person@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.emailVerifiedAt").value("2026-10-09T10:00:00Z"));
    }

    @Test
    @DisplayName("🔴 풀 계정 아님 → 404 ACCOUNT_NOT_FOUND · 불일치 → 403 ACCOUNT_EMAIL_MISMATCH · 미인증 → 403 EMAIL_NOT_VERIFIED")
    void match_refusals() throws Exception {
        given(matchUseCase.match(ACCOUNT, "a@x.example"))
                .willThrow(new AccountNotFoundException(ACCOUNT))
                .willThrow(new AccountEmailMismatchException("mismatch"))
                .willThrow(new EmailNotVerifiedException("not verified"));
        match("a@x.example").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        match("a@x.example").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_EMAIL_MISMATCH"));
        match("a@x.example").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("expectedEmail 없음 → 400 VALIDATION_ERROR · 유스케이스 미호출")
    void match_validation() throws Exception {
        mockMvc.perform(post(MATCH, ACCOUNT).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(matchUseCase);
    }

    @Test
    @DisplayName("mail 204 · 명령에 원문 토큰 전달")
    void mail_204() throws Exception {
        mockMvc.perform(post(MAIL).contentType(MediaType.APPLICATION_JSON).content(MAIL_BODY))
                .andExpect(status().isNoContent());
        ArgumentCaptor<SendOperatorInvitationMailCommand> cmd = ArgumentCaptor.forClass(SendOperatorInvitationMailCommand.class);
        verify(mailUseCase).send(cmd.capture());
        assertThat(cmd.getValue().token()).isEqualTo("raw-token-xyz");
        assertThat(cmd.getValue().expiresAt()).isEqualTo(Instant.parse("2026-10-17T10:00:00Z"));
    }

    @Test
    @DisplayName("mail 영구 → 422 INVITATION_EMAIL_UNDELIVERABLE · 일시 → 503 INVITATION_EMAIL_SEND_FAILED · 응답에 토큰 없음")
    void mail_failures() throws Exception {
        willThrow(new InvitationEmailSendFailedException(EmailDeliveryException.Kind.PERMANENT))
                .willThrow(new InvitationEmailSendFailedException(EmailDeliveryException.Kind.TRANSIENT))
                .given(mailUseCase).send(any());
        String body1 = mockMvc.perform(post(MAIL).contentType(MediaType.APPLICATION_JSON).content(MAIL_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_UNDELIVERABLE"))
                .andReturn().getResponse().getContentAsString();
        String body2 = mockMvc.perform(post(MAIL).contentType(MediaType.APPLICATION_JSON).content(MAIL_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_SEND_FAILED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body1 + body2).doesNotContain("raw-token-xyz").doesNotContain("person@example.com");
    }

    @Test
    @DisplayName("mail 필드 누락(token) → 400 · 메시지에 값이 실리지 않는다")
    void mail_validation() throws Exception {
        String body = mockMvc.perform(post(MAIL).contentType(MediaType.APPLICATION_JSON).content("""
                        {"to":"person@example.com","tenantId":"acme-corp","expiresAt":"2026-10-17T10:00:00Z"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("person@example.com");
        verifyNoInteractions(mailUseCase);
    }
}
