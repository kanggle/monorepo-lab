package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.RoleCatalogQueryService;
import com.example.admin.application.port.RolePermissionCatalogPort.RoleWithPermissions;
import com.example.admin.domain.rbac.Permission;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-486 — WebMvc slice for {@link RoleAdminController}
 * ({@code GET /api/admin/roles}, {@code GET /api/admin/permissions}). Mirrors the
 * {@code OperatorAdminControllerTest} setup: JWT fixture + RBAC aspect wired so
 * the permission gate is exercised end-to-end at the aspect level.
 */
@WebMvcTest(controllers = RoleAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        RoleAdminControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class RoleAdminControllerSliceTest {

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

    @MockitoBean RoleCatalogQueryService roleCatalogQueryService;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;

    @BeforeEach
    void grantAll() {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(true);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(true);
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-actor");
    }

    // ------------------------------------------------ GET /api/admin/roles

    @Test
    void list_roles_returns_catalog_with_permission_sets_and_global_scope() throws Exception {
        when(roleCatalogQueryService.listRoles()).thenReturn(List.of(
                new RoleWithPermissions(1L, "SUPER_ADMIN", "Full platform administrator",
                        List.of("account.lock", "operator.manage")),
                new RoleWithPermissions(2L, "SUPPORT_READONLY", "CS L1",
                        List.of("audit.read", "security.event.read"))));

        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("global"))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0].id").value(1))
                .andExpect(jsonPath("$.roles[0].name").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.roles[0].description").value("Full platform administrator"))
                .andExpect(jsonPath("$.roles[0].permissions[0]").value("account.lock"))
                .andExpect(jsonPath("$.roles[0].permissions[1]").value("operator.manage"))
                .andExpect(jsonPath("$.roles[1].name").value("SUPPORT_READONLY"));
    }

    @Test
    void list_roles_empty_catalog_returns_empty_array() throws Exception {
        when(roleCatalogQueryService.listRoles()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("global"))
                .andExpect(jsonPath("$.roles.length()").value(0));
    }

    @Test
    void list_roles_without_jwt_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/roles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void list_roles_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/admin/roles").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    // ------------------------------------------ GET /api/admin/permissions

    @Test
    void list_permissions_returns_full_catalog_with_global_scope() throws Exception {
        when(roleCatalogQueryService.listPermissions()).thenReturn(Permission.catalog());

        mockMvc.perform(get("/api/admin/permissions").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("global"))
                .andExpect(jsonPath("$.permissions.length()").value(Permission.catalog().size()))
                .andExpect(jsonPath("$.permissions[0]").value("account.read"));
    }

    @Test
    void list_permissions_without_jwt_returns_401() throws Exception {
        mockMvc.perform(get("/api/admin/permissions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void list_permissions_without_permission_returns_403() throws Exception {
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(get("/api/admin/permissions").header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
