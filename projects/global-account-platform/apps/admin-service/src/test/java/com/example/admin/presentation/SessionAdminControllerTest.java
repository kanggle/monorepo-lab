package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.RevokeSessionResult;
import com.example.admin.application.SessionAdminUseCase;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        SessionAdminControllerTest.JwtBeans.class})
class SessionAdminControllerTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SessionAdminUseCase useCase;

    @MockBean
    PermissionEvaluator permissionEvaluator;

    @MockBean
    AdminActionAuditor auditor;

    @BeforeEach
    void grantAll() {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-1");
    }

    @Test
    void revoke_success_returns_200() throws Exception {
        when(useCase.revoke(any())).thenReturn(new RevokeSessionResult(
                "acc-1", 3, "op-1", Instant.now(), "audit-1"));

        mockMvc.perform(post("/api/admin/sessions/acc-1/revoke")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-1")
                        .header("X-Operator-Reason", "security-incident")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessionCount").value(3))
                .andExpect(jsonPath("$.auditId").value("audit-1"));
    }

    @Test
    void revoke_missing_idempotency_key_returns_400_validation_error() throws Exception {
        mockMvc.perform(post("/api/admin/sessions/acc-1/revoke")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void revoke_missing_reason_returns_400_reason_required() throws Exception {
        mockMvc.perform(post("/api/admin/sessions/acc-1/revoke")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void revoke_without_required_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/admin/sessions/acc-1/revoke")
                        .header("Authorization", bearer())
                        .header("Idempotency-Key", "idemp-3")
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void revoke_without_jwt_returns_401_token_invalid() throws Exception {
        mockMvc.perform(post("/api/admin/sessions/acc-1/revoke")
                        .header("Idempotency-Key", "idemp-4")
                        .header("X-Operator-Reason", "fraud")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }
}
