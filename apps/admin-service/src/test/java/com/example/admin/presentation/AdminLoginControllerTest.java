package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.Outcome;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.exception.EnrollmentRequiredException;
import com.example.admin.application.exception.InvalidCredentialsException;
import com.example.admin.application.exception.InvalidLoginRequestException;
import com.example.admin.application.exception.InvalidRecoveryCodeException;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@code POST /api/admin/auth/login} (TASK-BE-029-3).
 *
 * <p>Mocks {@link AdminLoginService} and {@link AdminActionAuditor} so the
 * assertions focus on controller-level outcomes: HTTP status, response body,
 * and the audit record that is emitted on every path.
 */
@WebMvcTest(controllers = AdminAuthController.class)
@Import({AdminLoginControllerTest.Config.class, AdminExceptionHandler.class})
class AdminLoginControllerTest {

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-00000000dev1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AdminLoginService loginService;
    @MockBean AdminActionAuditor auditor;
    // Referenced by AdminAuthController (enroll/verify paths) — unused in login tests.
    @MockBean TotpEnrollmentService totpService;
    @MockBean BootstrapTokenService bootstrapTokenService;
    @MockBean com.example.admin.application.AdminRefreshTokenService refreshService;
    @MockBean com.example.admin.application.AdminLogoutService logoutService;

    @BeforeEach
    void setup() {
        when(auditor.newAuditId()).thenReturn("audit-1");
        doNothing().when(auditor).recordLogin(any(AdminActionAuditor.LoginAuditRecord.class));
    }

    @Test
    void enrollmentRequiredReturns401WithBootstrapToken() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), isNull(), isNull()))
                .thenThrow(new EnrollmentRequiredException("boot.tok.en", 600L));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"devpassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_REQUIRED"))
                .andExpect(jsonPath("$.bootstrapToken").value("boot.tok.en"))
                .andExpect(jsonPath("$.bootstrapExpiresIn").value(600));

        verifyAuditOutcome(Outcome.FAILURE, false);
    }

    @Test
    void missingTotpReturns401InvalidTwoFaCode() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), isNull(), isNull()))
                .thenThrow(new InvalidTwoFaCodeException("2FA required but not submitted"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"devpassword123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CODE"));

        verifyAuditOutcome(Outcome.FAILURE, false);
    }

    @Test
    void validTotpReturns200WithTokenAndTwofaUsedTrue() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), eq("123456"), isNull()))
                .thenReturn(new AdminLoginService.LoginResult("jwt.access.token", 3600L, "jwt.refresh.token", 2_592_000L, true));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"devpassword123!\",\"totpCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.access.token"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshToken").value("jwt.refresh.token"))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000));

        verifyAuditOutcome(Outcome.SUCCESS, true);
    }

    @Test
    void reusedRecoveryCodeReturns401() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), isNull(), eq("ABCD-EFGH-IJKL")))
                .thenThrow(new InvalidRecoveryCodeException("Recovery code does not match"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID
                                + "\",\"password\":\"devpassword123!\",\"recoveryCode\":\"ABCD-EFGH-IJKL\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_RECOVERY_CODE"));

        verifyAuditOutcome(Outcome.FAILURE, false);
    }

    @Test
    void invalidPasswordReturns401AndFailureAudit() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("wrong"), isNull(), isNull()))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        ArgumentCaptor<AdminActionAuditor.LoginAuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.LoginAuditRecord.class);
        verify(auditor).recordLogin(captor.capture());
        AdminActionAuditor.LoginAuditRecord row = captor.getValue();
        assertThat(row.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(row.twofaUsed()).isFalse();
        assertThat(row.downstreamDetail()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(row.targetType()).isEqualTo("OPERATOR");
        assertThat(row.targetId()).isEqualTo(OPERATOR_ID);
        assertThat(row.reason()).isEqualTo(AdminActionAuditor.REASON_SELF_LOGIN);
    }

    @Test
    void operatorWithoutRequire2faReturns200WithoutTotp() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), isNull(), isNull()))
                .thenReturn(new AdminLoginService.LoginResult("jwt.access.token", 3600L, "jwt.refresh.token", 2_592_000L, false));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"devpassword123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.access.token"));

        verifyAuditOutcome(Outcome.SUCCESS, false);
    }

    @Test
    void bothTotpAndRecoveryReturns400BadRequest() throws Exception {
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"),
                eq("123456"), eq("ABCD-EFGH-IJKL")))
                .thenThrow(new InvalidLoginRequestException(
                        "Exactly one of totpCode or recoveryCode must be provided"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID
                                + "\",\"password\":\"devpassword123!\","
                                + "\"totpCode\":\"123456\",\"recoveryCode\":\"ABCD-EFGH-IJKL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyAuditOutcome(Outcome.FAILURE, false);
    }

    @Test
    void loginWithout2faWhenRequired_returns_400_badRequest() throws Exception {
        // require_2fa=TRUE operator + enrollment completed + totp/recovery both
        // omitted → AdminLoginService throws InvalidLoginRequestException per
        // admin-api.md contract. See TASK-BE-029-3 AC Revision.
        when(loginService.login(eq(OPERATOR_ID), eq("devpassword123!"), isNull(), isNull()))
                .thenThrow(new InvalidLoginRequestException(
                        "Exactly one of totpCode or recoveryCode must be provided"));

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operatorId\":\"" + OPERATOR_ID + "\",\"password\":\"devpassword123!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyAuditOutcome(Outcome.FAILURE, false);
    }

    private void verifyAuditOutcome(Outcome expected, boolean expectedTwofaUsed) {
        ArgumentCaptor<AdminActionAuditor.LoginAuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.LoginAuditRecord.class);
        verify(auditor).recordLogin(captor.capture());
        AdminActionAuditor.LoginAuditRecord row = captor.getValue();
        assertThat(row.outcome()).isEqualTo(expected);
        assertThat(row.twofaUsed()).isEqualTo(expectedTwofaUsed);
        assertThat(row.targetType()).isEqualTo("OPERATOR");
        assertThat(row.targetId()).isEqualTo(OPERATOR_ID);
    }

    @TestConfiguration
    @EnableWebSecurity
    static class Config {
        @Bean
        public SecurityFilterChain testChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }
}
