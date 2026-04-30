package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.AdminLoginService;
import com.example.admin.application.AdminLogoutService;
import com.example.admin.application.AdminRefreshTokenService;
import com.example.admin.application.TotpEnrollmentService;
import com.example.admin.application.exception.TotpNotEnrolledException;
import com.example.admin.infrastructure.security.BootstrapTokenService;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuthController.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        TotpRecoveryCodeRegenerateControllerTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class TotpRecoveryCodeRegenerateControllerTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() { jwt = new OperatorJwtTestFixture(); }

    @TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    private static final String OPERATOR_ID = "00000000-0000-7000-8000-000000000099";

    @Autowired MockMvc mockMvc;
    @MockBean TotpEnrollmentService totpService;
    @MockBean AdminLoginService loginService;
    @MockBean AdminActionAuditor auditor;
    @MockBean BootstrapTokenService bootstrapTokenService;
    @MockBean AdminRefreshTokenService refreshService;
    @MockBean AdminLogoutService logoutService;

    private String bearer() {
        return "Bearer " + jwt.operatorToken(OPERATOR_ID);
    }

    @Test
    void regenerate200ReturnsRecoveryCodes() throws Exception {
        List<String> codes = List.of(
                "AAAA-BBBB-CCCC", "DDDD-EEEE-FFFF", "GGGG-HHHH-IIII",
                "JJJJ-KKKK-LLLL", "MMMM-NNNN-OOOO", "PPPP-QQQQ-RRRR",
                "SSSS-TTTT-UUUU", "VVVV-WWWW-XXXX", "YYYY-ZZZZ-1111",
                "2222-3333-4444");
        when(totpService.regenerateRecoveryCodes(eq(OPERATOR_ID))).thenReturn(codes);
        when(auditor.newAuditId()).thenReturn("audit-regen-1");

        mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andExpect(jsonPath("$.recoveryCodes[0]").value("AAAA-BBBB-CCCC"));
    }

    @Test
    void regenerate401WhenNoToken() throws Exception {
        mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regenerate404WhenTotpNotEnrolled() throws Exception {
        when(auditor.newAuditId()).thenReturn("audit-regen-2");
        doThrow(new TotpNotEnrolledException("TOTP not enrolled"))
                .when(totpService).regenerateRecoveryCodes(eq(OPERATOR_ID));

        mockMvc.perform(post("/api/admin/auth/2fa/recovery-codes/regenerate")
                        .header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOTP_NOT_ENROLLED"));
    }
}
