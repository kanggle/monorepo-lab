package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.TenantOrgNodePlacementUseCase;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantOrgNodeConflictException;
import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-625 — the HTTP shape of {@code PUT /api/admin/tenants/{tenantId}/org-node} and its
 * preview: the {@code org.manage} gate, the body rule ({@code orgNodeId} key required — null
 * detaches, a missing key must not), and the error-code mapping (404/404/409).
 */
@WebMvcTest(controllers = TenantOrgNodePlacementController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        TenantOrgNodePlacementControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class TenantOrgNodePlacementControllerSliceTest {

    private static OperatorJwtTestFixture jwt;
    private static final String ORG_ADMIN_ID = "op-org-admin";
    private static final String TENANT_ADMIN_ONLY_ID = "op-tenant-admin";

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

    @Autowired MockMvc mockMvc;

    @MockitoBean TenantOrgNodePlacementUseCase placementUseCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor adminActionAuditor; // required by RequiresPermissionAspect

    @BeforeEach
    void permissions() {
        lenient().when(permissionEvaluator.hasPermission(eq(ORG_ADMIN_ID), anyString())).thenReturn(true);
        // TENANT_ADMIN holds operator.manage but NOT org.manage.
        lenient().when(permissionEvaluator.hasPermission(TENANT_ADMIN_ONLY_ID, Permission.ORG_MANAGE)).thenReturn(false);
        lenient().when(permissionEvaluator.hasAllPermissions(anyString(), any())).thenReturn(true);
    }

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private static TenantPlacementView effect(String from, String to, boolean changed) {
        return new TenantPlacementView("acme-wms", from, to,
                List.of("finance", "wms"), List.of("wms"), List.of("finance"), List.of(), changed);
    }

    @Test
    @DisplayName("AC-3: TENANT_ADMIN alone (no org.manage) → 403 PERMISSION_DENIED; the use-case is never reached")
    void tenantAdminOnly_isDeniedAtTheGate() throws Exception {
        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(TENANT_ADMIN_ONLY_ID))
                        .header("X-Operator-Reason", "move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"erp-div\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(placementUseCase, never()).place(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("move → 200 with the effect and changed=true")
    void move_returnsEffect() throws Exception {
        when(placementUseCase.place(any(), eq("acme-wms"), eq("erp-div"), eq("reorg")))
                .thenReturn(effect("wms-div", "erp-div", true));

        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "reorg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"erp-div\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromOrgNodeId").value("wms-div"))
                .andExpect(jsonPath("$.toOrgNodeId").value("erp-div"))
                .andExpect(jsonPath("$.lostDomains[0]").value("finance"))
                .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    @DisplayName("{\"orgNodeId\": null} detaches")
    void explicitNull_detaches() throws Exception {
        when(placementUseCase.place(any(), eq("acme-wms"), isNull(), eq("spin off")))
                .thenReturn(effect("wms-div", null, true));

        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "spin off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toOrgNodeId").doesNotExist());
    }

    @Test
    @DisplayName("{} (key missing) → 400 VALIDATION_ERROR — a typo'd key must not silently detach")
    void missingKey_is400() throws Exception {
        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "oops")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeID\":\"erp-div\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(placementUseCase, never()).place(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("missing X-Operator-Reason → 400 REASON_REQUIRED")
    void missingReason_is400() throws Exception {
        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"erp-div\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    @DisplayName("source side refused → 404 TENANT_NOT_FOUND (not 403)")
    void sourceRefused_is404Tenant() throws Exception {
        when(placementUseCase.place(any(), eq("other-svc"), eq("erp-div"), any()))
                .thenThrow(new TenantNotFoundException("other-svc"));

        mockMvc.perform(put("/api/admin/tenants/other-svc/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "pull")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"erp-div\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    @DisplayName("destination refused / deleted meanwhile → 404 ORG_NODE_NOT_FOUND")
    void destinationRefused_is404Node() throws Exception {
        when(placementUseCase.place(any(), eq("acme-wms"), eq("foreign"), any()))
                .thenThrow(new OrgNodeNotFoundException("Org node not found: foreign"));

        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"foreign\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORG_NODE_NOT_FOUND"));
    }

    @Test
    @DisplayName("placement changed between check and write → 409 TENANT_ORG_NODE_CONFLICT")
    void conflict_is409() throws Exception {
        when(placementUseCase.place(any(), eq("acme-wms"), eq("erp-div"), any()))
                .thenThrow(new TenantOrgNodeConflictException("moved meanwhile"));

        mockMvc.perform(put("/api/admin/tenants/acme-wms/org-node")
                        .header("Authorization", bearer(ORG_ADMIN_ID))
                        .header("X-Operator-Reason", "move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgNodeId\":\"erp-div\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_ORG_NODE_CONFLICT"));
    }

    @Test
    @DisplayName("P2 preview: the effect without `changed`; orgNodeId omitted = detach")
    void preview_returnsEffect() throws Exception {
        when(placementUseCase.preview(any(), eq("acme-wms"), isNull()))
                .thenReturn(effect("wms-div", null, false));

        mockMvc.perform(get("/api/admin/tenants/acme-wms/org-node/preview")
                        .header("Authorization", bearer(ORG_ADMIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domainsBefore[1]").value("wms"))
                .andExpect(jsonPath("$.changed").doesNotExist());
    }
}
