package com.example.admin.presentation;

import com.example.admin.application.RoleCatalogQueryService;
import com.example.admin.application.port.RolePermissionCatalogPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.PermissionCatalogResponse;
import com.example.admin.presentation.dto.RoleCatalogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-486 — read-only role/permission catalog surface for the console
 * 「권한」 (TASK-PC-FE-227) / 「권한 세트」 (TASK-PC-FE-228) screens.
 *
 * <p>A dedicated controller (not {@code OperatorAdminController}) because the
 * role/permission catalog is a distinct resource from operator administration —
 * roles/permissions are seed/Flyway-owned reference data; operators are managed
 * records. v1 is <b>read-only</b>: role/permission definition (create/update/
 * delete) stays in migrations.
 *
 * <p><b>Permission gate — {@code operator.manage} (decision, TASK-BE-486
 * Acceptance Criteria).</b> Rather than introduce new {@code role.read} /
 * {@code permission.read} keys, both endpoints reuse {@code operator.manage},
 * the same key that already gates {@code GET /api/admin/operators/grantable-roles}
 * (the closest sibling — a read over the role catalog). The role/permission
 * catalog IS the reference data behind operator management: whoever assigns
 * roles needs to see the roles and their permission sets. {@code operator.manage}
 * is already granted to {@code SUPER_ADMIN} (V0022) and {@code TENANT_ADMIN}
 * (V0033), so the catalog is viewable with zero seed churn and no new key
 * fragmenting the RBAC model. See rbac.md § Permission Keys.
 *
 * <p><b>No {@code permission-sets} endpoint.</b> The optional
 * {@code GET /api/admin/permission-sets} is intentionally omitted:
 * {@code GET /api/admin/roles} already returns each role WITH its permission-key
 * set, which is exactly the permission-set view TASK-PC-FE-228 needs (a role
 * framed as a set of permissions). A second endpoint over the same table would
 * be redundant.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleCatalogQueryService roleCatalogQueryService;

    /**
     * The role catalog — every seed role with its permission-key set, in stable
     * seed (role-id ASC) order, mirroring {@code grantable-roles}. Global scope.
     */
    @GetMapping("/roles")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<RoleCatalogResponse> listRoles() {
        List<RolePermissionCatalogPort.RoleWithPermissions> roles =
                roleCatalogQueryService.listRoles();
        List<RoleCatalogResponse.RoleItem> items = new ArrayList<>(roles.size());
        for (RolePermissionCatalogPort.RoleWithPermissions r : roles) {
            items.add(new RoleCatalogResponse.RoleItem(
                    r.id(), r.name(), r.description(), r.permissionKeys()));
        }
        return ResponseEntity.ok(new RoleCatalogResponse(
                RoleCatalogQueryService.GLOBAL_SCOPE, items));
    }

    /**
     * The full permission-key catalog (rbac.md § Permission Keys). Global scope.
     */
    @GetMapping("/permissions")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<PermissionCatalogResponse> listPermissions() {
        return ResponseEntity.ok(new PermissionCatalogResponse(
                RoleCatalogQueryService.GLOBAL_SCOPE,
                roleCatalogQueryService.listPermissions()));
    }
}
