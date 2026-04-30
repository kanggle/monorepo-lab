package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.AdminLogoutService;
import com.example.admin.application.AdminRefreshTokenService;
import com.example.admin.application.Outcome;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.support.OperatorJwtTestFixture;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@code POST /api/admin/auth/refresh} (TASK-BE-040). Covers
 * the contract surface only; AdminRefreshTokenService is mocked.
 */
@WebMvcTest(controllers = AdminAuthController.class)
@Import({AdminRefreshControllerTest.Config.class, AdminExceptionHandler.class})
class AdminRefreshControllerTest {

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-00000000dev1";
    // TASK-BE-040-fix — controller no longer decodes the JWT payload; the
    // token merely needs to parse as a JWS and the service (mocked) decides
    // the outcome. A real RS256-signed refresh token is used to satisfy the
    // post-fix rule that no alg:none or unsigned payload be trusted.
    private static final OperatorJwtTestFixture JWT_FIXTURE = new OperatorJwtTestFixture();
    private static final String SIGNED_REFRESH_TOKEN = JWT_FIXTURE.signRefresh(
            OPERATOR_ID,
            "11111111-1111-1111-1111-111111111111",
            java.time.Instant.now().plus(java.time.Duration.ofDays(30)));

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AdminRefreshTokenService refreshService;
    @MockBean AdminLogoutService logoutService;
    @MockBean AdminLoginService loginService;
    @MockBean TotpEnrollmentService totpService;
    @MockBean BootstrapTokenService bootstrapTokenService;
    @MockBean AdminActionAuditor auditor;

    @BeforeEach
    void setUp() {
        when(auditor.newAuditId()).thenReturn("audit-refresh-1");
        doNothing().when(auditor).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    void rotates_and_returns_new_token_pair() throws Exception {
        when(refreshService.refresh(anyString())).thenReturn(
                new AdminRefreshTokenService.RefreshResult(
                        "new.access.jwt", 3600L, "new.refresh.jwt", 2_592_000L, OPERATOR_ID));

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + SIGNED_REFRESH_TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new.access.jwt"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.refreshToken").value("new.refresh.jwt"))
                .andExpect(jsonPath("$.refreshExpiresIn").value(2_592_000));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(cap.capture());
        AdminActionAuditor.AuditRecord row = cap.getValue();
        assertThat(row.actionCode()).isEqualTo(ActionCode.OPERATOR_REFRESH);
        assertThat(row.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(row.targetType()).isEqualTo("OPERATOR");
        assertThat(row.targetId()).isEqualTo(OPERATOR_ID);
        assertThat(row.reason()).isEqualTo(AdminActionAuditor.REASON_SELF_REFRESH);
    }

    @Test
    void returns_401_invalid_refresh_token_when_jti_unknown() throws Exception {
        when(refreshService.refresh(anyString()))
                .thenThrow(new InvalidRefreshTokenException("Refresh token jti not registered"));

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + SIGNED_REFRESH_TOKEN + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(cap.getValue().downstreamDetail()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void returns_401_reuse_detected_on_revoked_jti_replay() throws Exception {
        when(refreshService.refresh(anyString()))
                .thenThrow(new RefreshTokenReuseDetectedException("chain invalidated", OPERATOR_ID));

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + SIGNED_REFRESH_TOKEN + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSE_DETECTED"));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(cap.capture());
        assertThat(cap.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(cap.getValue().downstreamDetail()).isEqualTo("REUSE_DETECTED");
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
