package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.AdminLogoutService;
import com.example.admin.application.AdminRefreshTokenService;
import com.example.admin.application.Outcome;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.security.jwt.JwtVerifier;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@code POST /api/admin/auth/logout} (TASK-BE-040). Uses the
 * shared {@link SliceTestSecurityConfig} so the operator JWT path is exercised
 * end-to-end through the production filter (minus the blacklist port — a
 * permissive mock is wired here so the access token survives the filter).
 */
@WebMvcTest(controllers = AdminAuthController.class)
@Import({AdminLogoutControllerTest.Config.class, SliceTestSecurityConfig.class, AdminExceptionHandler.class})
class AdminLogoutControllerTest {

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-00000000dev1";
    private static final String JTI = "11111111-1111-1111-1111-111111111111";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OperatorJwtTestFixture jwtFixture;
    @MockBean AdminLogoutService logoutService;
    @MockBean AdminRefreshTokenService refreshService;
    @MockBean AdminLoginService loginService;
    @MockBean TotpEnrollmentService totpService;
    @MockBean BootstrapTokenService bootstrapTokenService;
    @MockBean AdminActionAuditor auditor;

    @BeforeEach
    void setup() {
        when(auditor.newAuditId()).thenReturn("audit-logout-1");
        doNothing().when(auditor).record(any(AdminActionAuditor.AuditRecord.class));
    }

    @Test
    void returns_204_and_calls_logout_service_with_jti() throws Exception {
        String token = jwtFixture.operatorToken(OPERATOR_ID, JTI);

        mockMvc.perform(post("/api/admin/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(logoutService).logout(eq(OPERATOR_ID), eq(JTI), any(Instant.class), isNull());

        ArgumentCaptor<AdminActionAuditor.AuditRecord> cap =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(cap.capture());
        AdminActionAuditor.AuditRecord row = cap.getValue();
        assertThat(row.actionCode()).isEqualTo(ActionCode.OPERATOR_LOGOUT);
        assertThat(row.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(row.targetType()).isEqualTo("OPERATOR");
        assertThat(row.targetId()).isEqualTo(OPERATOR_ID);
    }

    @Test
    void forwards_optional_refresh_token_to_logout_service() throws Exception {
        String token = jwtFixture.operatorToken(OPERATOR_ID, JTI);

        mockMvc.perform(post("/api/admin/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"some.refresh.token\"}"))
                .andExpect(status().isNoContent());

        verify(logoutService).logout(eq(OPERATOR_ID), eq(JTI), any(Instant.class), eq("some.refresh.token"));
    }

    @Test
    void rejects_unauthenticated_request() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class Config {
        @Bean
        public OperatorJwtTestFixture jwtFixture() {
            return new OperatorJwtTestFixture();
        }
        @Bean
        public JwtVerifier operatorJwtVerifier(OperatorJwtTestFixture f) {
            return f.verifier();
        }
        @Bean
        public TokenBlacklistPort tokenBlacklistPort() {
            // Permissive mock so the access token always survives the filter
            // for the logout test surface.
            return new TokenBlacklistPort() {
                @Override public void blacklist(String jti, java.time.Duration ttl) {}
                @Override public boolean isBlacklisted(String jti) { return false; }
            };
        }
    }
}
