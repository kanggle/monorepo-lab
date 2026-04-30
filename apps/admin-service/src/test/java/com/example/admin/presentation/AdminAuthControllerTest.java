package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.exception.InvalidTwoFaCodeException;
import com.example.admin.infrastructure.security.BootstrapAuthenticationFilter;
import com.example.admin.infrastructure.security.BootstrapContext;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuthController.class)
@Import({AdminAuthControllerTest.Config.class, AdminExceptionHandler.class})
class AdminAuthControllerTest {

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-000000000001";
    private static final String JTI = "11111111-1111-7111-8111-111111111111";
    private static final String VALID_BEARER = "Bearer fake.bootstrap.token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TotpEnrollmentService totpService;
    @MockBean AdminLoginService loginService;
    @MockBean AdminActionAuditor auditor;
    @MockBean BootstrapTokenService bootstrapTokenService;
    @MockBean com.example.admin.application.AdminRefreshTokenService refreshService;
    @MockBean com.example.admin.application.AdminLogoutService logoutService;

    @BeforeEach
    void setup() {
        // Default happy path for the bootstrap filter: token parses and returns a context.
        when(bootstrapTokenService.verifyAndConsume(eq("fake.bootstrap.token"), any()))
                .thenReturn(new BootstrapContext(OPERATOR_ID, JTI));
        when(bootstrapTokenService.issue(any(), any()))
                .thenReturn(new BootstrapTokenService.Issued("verify.bootstrap.token", "jti-v",
                        java.time.Instant.now().plusSeconds(600)));
        when(auditor.newAuditId()).thenReturn("audit-1");
    }

    @Test
    void enroll200ReturnsOtpauthAndRecoveryCodes() throws Exception {
        when(totpService.enroll(OPERATOR_ID)).thenReturn(
                new TotpEnrollmentService.EnrollmentResult(
                        "otpauth://totp/admin-service:op@example.com?secret=ABC&issuer=admin-service&algorithm=SHA1&digits=6&period=30",
                        List.of("A1B2-C3D4-E5F6", "G7H8-I9J0-K1L2"),
                        Instant.parse("2026-04-14T10:00:00Z")));

        mockMvc.perform(post("/api/admin/auth/2fa/enroll")
                        .header("Authorization", VALID_BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2))
                .andExpect(jsonPath("$.enrolledAt").value("2026-04-14T10:00:00Z"))
                .andExpect(jsonPath("$.bootstrapToken").value("verify.bootstrap.token"))
                .andExpect(jsonPath("$.bootstrapTokenTtlSeconds").isNumber());

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        AdminActionAuditor.AuditRecord row = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(row.actionCode().name()).isEqualTo("OPERATOR_2FA_ENROLL");
        org.assertj.core.api.Assertions.assertThat(row.targetType()).isEqualTo("OPERATOR");
        org.assertj.core.api.Assertions.assertThat(row.targetId()).isEqualTo(OPERATOR_ID);
        org.assertj.core.api.Assertions.assertThat(row.reason()).isEqualTo("<self_enrollment>");
    }

    @Test
    void enrollWithoutBootstrapToken401() throws Exception {
        mockMvc.perform(post("/api/admin/auth/2fa/enroll"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_BOOTSTRAP_TOKEN"));
    }

    @Test
    void verify200OnValidCode() throws Exception {
        doNothing().when(totpService).verify(eq(OPERATOR_ID), eq("123456"));
        mockMvc.perform(post("/api/admin/auth/2fa/verify")
                        .header("Authorization", VALID_BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totpCode\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
        verify(auditor).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    void verify401OnInvalidCode() throws Exception {
        doThrow(new InvalidTwoFaCodeException("nope"))
                .when(totpService).verify(eq(OPERATOR_ID), eq("000000"));
        mockMvc.perform(post("/api/admin/auth/2fa/verify")
                        .header("Authorization", VALID_BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totpCode\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_2FA_CODE"));
    }

    @Test
    void verifyRejects4DigitCodeAsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/auth/2fa/verify")
                        .header("Authorization", VALID_BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totpCode\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * Slice-local security config — permits the 2FA sub-tree so the
     * {@link BootstrapAuthenticationFilter} governs 401 emission (matching
     * production {@code SecurityConfig}).
     */
    @TestConfiguration
    @EnableWebSecurity
    static class Config {
        @Bean
        public BootstrapAuthenticationFilter bootstrapAuthenticationFilter(BootstrapTokenService service) {
            return new BootstrapAuthenticationFilter(service);
        }

        @Bean
        public SecurityFilterChain testChain(HttpSecurity http,
                                             BootstrapAuthenticationFilter bootstrapFilter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterBefore(bootstrapFilter, UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }
}
