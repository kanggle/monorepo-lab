package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ManageSubscriptionUseCase;
import com.example.admin.application.exception.SubscriptionTransitionInvalidException;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SubscriptionAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        SubscriptionAdminControllerTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class SubscriptionAdminControllerTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ManageSubscriptionUseCase useCase;

    @MockitoBean
    PermissionEvaluator permissionEvaluator;

    @MockitoBean
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
    void subscribe_success_returns_201() throws Exception {
        when(useCase.subscribe(eq("acme-corp"), eq("scm"), any(), eq("new contract")))
                .thenReturn(new SubscriptionMutationSummary(
                        "acme-corp", "scm", null, "ACTIVE", Instant.parse("2026-06-10T00:00:00Z")));

        mockMvc.perform(post("/api/admin/subscriptions")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "new contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme-corp\",\"domainKey\":\"scm\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.previousStatus").doesNotExist());
    }

    @Test
    void changeStatus_success_returns_200() throws Exception {
        when(useCase.changeStatus(eq("acme-corp"), eq("wms"), eq("SUSPENDED"), any(), eq("past due")))
                .thenReturn(new SubscriptionMutationSummary(
                        "acme-corp", "wms", "ACTIVE", "SUSPENDED", Instant.parse("2026-06-10T00:00:00Z")));

        mockMvc.perform(patch("/api/admin/subscriptions/acme-corp/wms/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "past due")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.currentStatus").value("SUSPENDED"));
    }

    @Test
    void subscribe_missing_reason_returns_400_reason_required() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme-corp\",\"domainKey\":\"scm\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void subscribe_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/admin/subscriptions")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "new contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme-corp\",\"domainKey\":\"scm\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void subscribe_without_jwt_returns_401() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions")
                        .header("X-Operator-Reason", "new contract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":\"acme-corp\",\"domainKey\":\"scm\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void changeStatus_illegal_transition_returns_409() throws Exception {
        when(useCase.changeStatus(eq("acme-corp"), eq("wms"), eq("ACTIVE"), any(), anyString()))
                .thenThrow(new SubscriptionTransitionInvalidException(
                        "Illegal subscription transition for (acme-corp, wms)"));

        mockMvc.perform(patch("/api/admin/subscriptions/acme-corp/wms/status")
                        .header("Authorization", bearer())
                        .header("X-Operator-Reason", "resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_TRANSITION_INVALID"));
    }
}
