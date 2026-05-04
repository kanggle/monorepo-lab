package com.example.admin.presentation.tenant;

import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantIdReservedException;
import com.example.admin.application.tenant.CreateTenantUseCase;
import com.example.admin.application.tenant.GetTenantUseCase;
import com.example.admin.application.tenant.ListTenantsUseCase;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.application.tenant.UpdateTenantUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TenantAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        TenantAdminControllerTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin"})
class TenantAdminControllerTest {

    private static OperatorJwtTestFixture jwt;
    private static final String SUPER_ADMIN_ID = "op-super-001";
    private static final String REGULAR_OP_ID  = "op-regular-001";

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

    @MockBean CreateTenantUseCase createTenantUseCase;
    @MockBean UpdateTenantUseCase updateTenantUseCase;
    @MockBean GetTenantUseCase getTenantUseCase;
    @MockBean ListTenantsUseCase listTenantsUseCase;
    @MockBean PermissionEvaluator permissionEvaluator;
    @MockBean AdminOperatorJpaRepository operatorRepository;
    @MockBean AdminActionAuditor adminActionAuditor; // required by RequiresPermissionAspect

    // Pre-created mocks — stubs are set in @BeforeEach to avoid interleaving
    // with when(...).thenReturn(Optional.of(helperMethod())) call chains.
    private AdminOperatorJpaEntity superAdminMock;
    private AdminOperatorJpaEntity regularOpMock;

    private static TenantSummary stubTenant(String tenantId) {
        return new TenantSummary(tenantId, "Display " + tenantId,
                "B2B_ENTERPRISE", "ACTIVE", Instant.now(), Instant.now());
    }

    @BeforeEach
    void setupMocks() {
        // Create the entity mocks fresh for each test (MockBean resets between tests).
        superAdminMock = Mockito.mock(AdminOperatorJpaEntity.class);
        lenient().when(superAdminMock.getOperatorId()).thenReturn(SUPER_ADMIN_ID);
        lenient().when(superAdminMock.getEmail()).thenReturn("super@example.com");
        lenient().when(superAdminMock.getDisplayName()).thenReturn("Super Admin");
        lenient().when(superAdminMock.getStatus()).thenReturn("ACTIVE");
        lenient().when(superAdminMock.getVersion()).thenReturn(0);   // int field
        lenient().when(superAdminMock.getTenantId()).thenReturn("*");

        regularOpMock = Mockito.mock(AdminOperatorJpaEntity.class);
        lenient().when(regularOpMock.getOperatorId()).thenReturn(REGULAR_OP_ID);
        lenient().when(regularOpMock.getEmail()).thenReturn("reg@example.com");
        lenient().when(regularOpMock.getDisplayName()).thenReturn("Regular Op");
        lenient().when(regularOpMock.getStatus()).thenReturn("ACTIVE");
        lenient().when(regularOpMock.getVersion()).thenReturn(0);    // int field
        lenient().when(regularOpMock.getTenantId()).thenReturn("tenant-a");

        // SUPER_ADMIN: all permissions granted
        lenient().when(permissionEvaluator.hasPermission(eq(SUPER_ADMIN_ID), anyString())).thenReturn(true);
        // Regular operator: no permissions (tenant.manage is denied)
        lenient().when(permissionEvaluator.hasPermission(eq(REGULAR_OP_ID), anyString())).thenReturn(false);
        lenient().when(permissionEvaluator.hasAllPermissions(anyString(), any())).thenReturn(true);

        // isTenantAllowed:
        //   - SUPER_ADMIN (tenantId='*'): allowed everywhere
        //   - Regular op (tenantId='tenant-a'): allowed only for own tenant, denied for others
        // Use lenient() + argThat() so unused stubs do not cause failures.
        lenient().when(permissionEvaluator.isTenantAllowed(
                Mockito.argThat(op -> op != null && "*".equals(op.tenantId())),
                anyString())).thenReturn(true);
        lenient().when(permissionEvaluator.isTenantAllowed(
                Mockito.argThat(op -> op != null && "tenant-a".equals(op.tenantId())),
                eq("tenant-a"))).thenReturn(true);
        lenient().when(permissionEvaluator.isTenantAllowed(
                Mockito.argThat(op -> op != null && "tenant-a".equals(op.tenantId())),
                eq("tenant-b"))).thenReturn(false);
    }

    private String superAdminToken() {
        return "Bearer " + jwt.operatorToken(SUPER_ADMIN_ID);
    }

    private String regularOpToken() {
        return "Bearer " + jwt.operatorToken(REGULAR_OP_ID);
    }

    // ---- POST /api/admin/tenants -------------------------------------------

    @Test
    void create_tenant_super_admin_returns_201() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(createTenantUseCase.execute(eq("wms-test"), anyString(), anyString(), any(), any(), any()))
                .thenReturn(stubTenant("wms-test"));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "onboard wms tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantId").value("wms-test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void create_tenant_missing_operator_reason_returns_400_reason_required() throws Exception {
        // X-Operator-Reason is required per admin-api.md §Authentication; missing header
        // is mapped to 400 REASON_REQUIRED by AdminExceptionHandler.handleMissingHeader.
        // Aspect passes (operator has tenant.manage) → @RequestHeader binding fires the error.
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void create_tenant_regular_operator_returns_403_permission_denied() throws Exception {
        // Regular operator lacks tenant.manage permission. With X-Operator-Reason supplied,
        // arg resolution succeeds and the RequiresPermissionAspect fires → PermissionDeniedException
        // → PERMISSION_DENIED. (Without the header, REASON_REQUIRED would short-circuit at the
        // arg resolver before the aspect runs — separate test for that path.)

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", regularOpToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS Test","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void create_tenant_invalid_slug_returns_400_validation_error() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(createTenantUseCase.execute(eq("WMS"), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("tenantId must match regex"));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"WMS","displayName":"WMS","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_tenant_reserved_returns_400_tenant_id_reserved() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(createTenantUseCase.execute(eq("admin"), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new TenantIdReservedException("admin"));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"admin","displayName":"Admin","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TENANT_ID_RESERVED"));
    }

    @Test
    void create_tenant_duplicate_returns_409_tenant_already_exists() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(createTenantUseCase.execute(anyString(), anyString(), anyString(), any(), any(), any()))
                .thenThrow(new TenantAlreadyExistsException("wms-test"));

        mockMvc.perform(post("/api/admin/tenants")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":"wms-test","displayName":"WMS","tenantType":"B2B_ENTERPRISE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TENANT_ALREADY_EXISTS"));
    }

    // ---- PATCH /api/admin/tenants/{tenantId} --------------------------------

    @Test
    void patch_tenant_status_suspended_super_admin_returns_200() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        TenantSummary suspended = new TenantSummary("wms-test", "WMS Test",
                "B2B_ENTERPRISE", "SUSPENDED", Instant.now(), Instant.now());
        when(updateTenantUseCase.execute(eq("wms-test"), any(), eq("SUSPENDED"), any(), any(), any()))
                .thenReturn(suspended);

        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken())
                        .header("X-Operator-Reason", "suspend wms tenant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void patch_tenant_missing_operator_reason_returns_400_reason_required() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));

        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", superAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }

    @Test
    void patch_tenant_regular_operator_returns_403_permission_denied() throws Exception {
        // Regular operator lacks tenant.manage — with X-Operator-Reason supplied so arg
        // resolution passes and the aspect fires → PERMISSION_DENIED.
        mockMvc.perform(patch("/api/admin/tenants/wms-test")
                        .header("Authorization", regularOpToken())
                        .header("X-Operator-Reason", "regression test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SUSPENDED"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ---- GET /api/admin/tenants --------------------------------------------

    @Test
    void list_tenants_super_admin_returns_200_paginated() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(listTenantsUseCase.execute(any(), any(), eq(0), eq(20)))
                .thenReturn(new TenantPageSummary(
                        List.of(stubTenant("fan-platform")), 0, 20, 1, 1));

        mockMvc.perform(get("/api/admin/tenants")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ---- GET /api/admin/tenants/{tenantId} --------------------------------

    @Test
    void get_tenant_super_admin_returns_200() throws Exception {
        when(operatorRepository.findByOperatorId(SUPER_ADMIN_ID))
                .thenReturn(Optional.of(superAdminMock));
        when(getTenantUseCase.execute("tenant-a")).thenReturn(stubTenant("tenant-a"));

        mockMvc.perform(get("/api/admin/tenants/tenant-a")
                        .header("Authorization", superAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"));
    }

    @Test
    void get_tenant_scoped_operator_own_tenant_returns_200() throws Exception {
        when(operatorRepository.findByOperatorId(REGULAR_OP_ID))
                .thenReturn(Optional.of(regularOpMock));
        when(getTenantUseCase.execute("tenant-a")).thenReturn(stubTenant("tenant-a"));

        mockMvc.perform(get("/api/admin/tenants/tenant-a")
                        .header("Authorization", regularOpToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"));
    }

    @Test
    void get_tenant_scoped_operator_other_tenant_returns_403() throws Exception {
        when(operatorRepository.findByOperatorId(REGULAR_OP_ID))
                .thenReturn(Optional.of(regularOpMock));

        mockMvc.perform(get("/api/admin/tenants/tenant-b")
                        .header("Authorization", regularOpToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_SCOPE_DENIED"));
    }
}
