package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorInvitationAcceptResult;
import com.example.admin.application.OperatorInvitationAcceptanceUseCase;
import com.example.admin.application.OperatorInvitationPreviewResult;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationEmailNotVerifiedException;
import com.example.admin.application.exception.OperatorInvitationExpiredException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.infrastructure.config.InternalApiFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S3 — controller slice for {@code POST /internal/operator-invitations/preview · /accept}
 * (auth-to-admin.md): the response shapes, the refusal codes through {@code AdminExceptionHandler}, and the
 * request records' redacting {@code toString} (R4 — Spring logs the body via {@code toString} at DEBUG).
 */
@WebMvcTest(OperatorInvitationAcceptanceController.class)
@Import(OperatorInvitationAcceptanceControllerSliceTest.BypassInternalSecurity.class)
@DisplayName("OperatorInvitationAcceptanceController slice tests (TASK-MONO-772 S3)")
class OperatorInvitationAcceptanceControllerSliceTest {

    private static final String TOKEN = "raw-token-should-never-be-printed";
    private static final String ACCOUNT = "0199de70-0000-7000-8000-0000000acc01";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private OperatorInvitationAcceptanceUseCase useCase;

    private static String acceptBody() {
        return "{\"token\":\"" + TOKEN + "\",\"accountId\":\"" + ACCOUNT + "\"}";
    }

    @Test
    @DisplayName("preview 200 — 계약 모양, tenantDisplayName 이 null 이어도 키는 남는다")
    void preview_200() throws Exception {
        given(useCase.preview(TOKEN)).willReturn(new OperatorInvitationPreviewResult(
                "acme-corp", null, "p*****@example.com", List.of("SUPPORT_LOCK"), "PENDING", false,
                Instant.parse("2026-10-17T10:00:00Z")));

        String json = mockMvc.perform(post("/internal/operator-invitations/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.maskedEmail").value("p*****@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_LOCK"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-17T10:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"tenantDisplayName\":null").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("preview 404 OPERATOR_INVITATION_NOT_FOUND")
    void preview_404() throws Exception {
        given(useCase.preview(TOKEN)).willThrow(new OperatorInvitationNotFoundException("Operator invitation not found"));

        mockMvc.perform(post("/internal/operator-invitations/preview")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("accept 200 · alreadyAccepted 200")
    void accept_200() throws Exception {
        given(useCase.accept(TOKEN, ACCOUNT)).willReturn(
                new OperatorInvitationAcceptResult("op-new", "acme-corp", List.of("SUPPORT_LOCK"), false),
                new OperatorInvitationAcceptResult("op-new", "acme-corp", List.of("SUPPORT_LOCK"), true));

        mockMvc.perform(post("/internal/operator-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value("op-new"))
                .andExpect(jsonPath("$.tenantId").value("acme-corp"))
                .andExpect(jsonPath("$.roles[0]").value("SUPPORT_LOCK"))
                .andExpect(jsonPath("$.alreadyAccepted").value(false));
        mockMvc.perform(post("/internal/operator-invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyAccepted").value(true));
    }

    @Test
    @DisplayName("거절 코드가 계약 status 로: 403 EMAIL_NOT_VERIFIED · 410 EXPIRED · 409 ALREADY_PROVISIONED · 409 EMAIL_CONFLICT · 503 DOWNSTREAM_ERROR")
    void accept_refusals() throws Exception {
        given(useCase.accept(TOKEN, ACCOUNT)).willThrow(
                new OperatorInvitationEmailNotVerifiedException("x"),
                new OperatorInvitationExpiredException("x"),
                new OperatorAlreadyProvisionedException("x"),
                new OperatorEmailConflictException("x"),
                new DownstreamFailureException("x", null));

        String[][] expected = {
                {"403", "EMAIL_NOT_VERIFIED"}, {"410", "OPERATOR_INVITATION_EXPIRED"},
                {"409", "OPERATOR_ALREADY_PROVISIONED"}, {"409", "OPERATOR_EMAIL_CONFLICT"},
                {"503", "DOWNSTREAM_ERROR"}};
        for (String[] e : expected) {
            mockMvc.perform(post("/internal/operator-invitations/accept")
                            .contentType(MediaType.APPLICATION_JSON).content(acceptBody()))
                    .andExpect(status().is(Integer.parseInt(e[0])))
                    .andExpect(jsonPath("$.code").value(e[1]));
        }
    }

    @Test
    @DisplayName("🔴 R4: 요청 레코드의 toString 은 토큰을 찍지 않는다")
    void requestRecords_redact() {
        assertThat(new OperatorInvitationAcceptanceController.AcceptRequest(TOKEN, ACCOUNT).toString())
                .doesNotContain(TOKEN).contains("<redacted>").contains(ACCOUNT);
        assertThat(new OperatorInvitationAcceptanceController.PreviewRequest(TOKEN).toString())
                .doesNotContain(TOKEN).contains("<redacted>");
    }

    @TestConfiguration
    @EnableWebSecurity
    static class BypassInternalSecurity {
        @Bean
        InternalApiFilter internalApiFilter() {
            return new InternalApiFilter(true); // dev/test bypass
        }

        @Bean
        SecurityFilterChain testInternalChain(HttpSecurity http, InternalApiFilter internalApiFilter)
                throws Exception {
            http
                    .securityMatcher("/internal/**")
                    .csrf(AbstractHttpConfigurer::disable)
                    .addFilterBefore(internalApiFilter, BearerTokenAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
            return http.build();
        }
    }
}
