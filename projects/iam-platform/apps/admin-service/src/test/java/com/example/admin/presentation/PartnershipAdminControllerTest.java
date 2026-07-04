package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.ManagePartnershipParticipantUseCase;
import com.example.admin.application.PartnershipManagementUseCase;
import com.example.admin.application.exception.PartnershipScopeInvalidException;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.domain.rbac.ScopeSet;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-477 / ADR-MONO-045 — {@code @WebMvcTest} slice for
 * {@link PartnershipAdminController}: permission gate, reason gate, 201/200/403/422
 * mapping. The full cross-org confinement path is proven by the Testcontainers IT.
 */
@WebMvcTest(controllers = PartnershipAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class, PartnershipAdminControllerTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class PartnershipAdminControllerTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) {
                jwt = new OperatorJwtTestFixture();
            }
            return jwt.verifier();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PartnershipManagementUseCase managementUseCase;

    @MockitoBean
    ManagePartnershipParticipantUseCase participantUseCase;

    @MockitoBean
    PermissionEvaluator permissionEvaluator;

    @MockitoBean
    AdminActionAuditor auditor;

    @BeforeEach
    void grantAll() {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-host");
    }

    private PartnershipView pending() {
        return new PartnershipView(1L, "pid-1", "acme-corp", "globex", PartnershipStatus.PENDING,
                ScopeSet.of(List.of("wms"), List.of("WMS_OP")), null, null,
                Instant.parse("2026-07-04T10:00:00Z"), null, null);
    }

    private static final String INVITE_BODY = """
            {"partnerTenantId":"globex","delegatedScope":{"domains":["wms"],"roles":["WMS_OP"]}}
            """;

    @Test
    @DisplayName("invite success → 201 with partnershipId + status PENDING")
    void invite_created() throws Exception {
        when(managementUseCase.invite(anyString(), any(), any(), anyString())).thenReturn(pending());

        mockMvc.perform(post("/api/admin/partnerships")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "collab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVITE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnershipId").value("pid-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.delegatedScope.domains[0]").value("wms"));
    }

    @Test
    @DisplayName("invite missing X-Operator-Reason → 400 REASON_REQUIRED")
    void invite_missingReason() throws Exception {
        mockMvc.perform(post("/api/admin/partnerships")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVITE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("invite without partnership.manage → 403 PERMISSION_DENIED")
    void invite_permissionDenied() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/admin/partnerships")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "collab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVITE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("invite with admin role in delegatedScope → 422 PARTNERSHIP_SCOPE_INVALID")
    void invite_scopeInvalid() throws Exception {
        when(managementUseCase.invite(anyString(), any(), any(), anyString()))
                .thenThrow(new PartnershipScopeInvalidException("admin role forbidden"));

        mockMvc.perform(post("/api/admin/partnerships")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "collab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"partnerTenantId":"globex","delegatedScope":{"domains":["wms"],"roles":["TENANT_ADMIN"]}}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARTNERSHIP_SCOPE_INVALID"));
    }

    @Test
    @DisplayName("accept success → 200 ACTIVE")
    void accept_ok() throws Exception {
        PartnershipView active = new PartnershipView(1L, "pid-1", "acme-corp", "globex",
                PartnershipStatus.ACTIVE, ScopeSet.of(List.of("wms"), List.of("WMS_OP")),
                null, null, Instant.parse("2026-07-04T10:00:00Z"),
                Instant.parse("2026-07-04T10:05:00Z"), null);
        when(managementUseCase.accept(anyString(), anyString(), any(), anyString())).thenReturn(active);

        mockMvc.perform(post("/api/admin/partnerships/pid-1:accept")
                        .header("Authorization", bearer())
                        .header("X-Tenant-Id", "globex")
                        .header("X-Operator-Reason", "accepting")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("invite without JWT → 401 TOKEN_INVALID")
    void invite_noJwt() throws Exception {
        mockMvc.perform(post("/api/admin/partnerships")
                        .header("X-Tenant-Id", "acme-corp")
                        .header("X-Operator-Reason", "collab")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVITE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
