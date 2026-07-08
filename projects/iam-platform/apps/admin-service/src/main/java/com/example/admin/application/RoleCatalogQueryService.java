package com.example.admin.application;

import com.example.admin.application.port.RolePermissionCatalogPort;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TASK-BE-486 — read-only orchestration for the console 「권한」 / 「권한 세트」
 * screens (TASK-PC-FE-227 / TASK-PC-FE-228). Exposes:
 *
 * <ul>
 *   <li>the role catalog with each role's permission-key set
 *       ({@code GET /api/admin/roles}), and</li>
 *   <li>the full permission-key catalog ({@code GET /api/admin/permissions}).</li>
 * </ul>
 *
 * <p><b>Scope is global.</b> {@code admin_roles} / {@code admin_role_permissions}
 * carry no tenant column — the RBAC catalog is a platform-wide definition
 * (data-model.md § admin_roles). Both responses advertise {@code scope="global"}
 * so the console never mistakes them for tenant-scoped data (task Implementation
 * Notes / Edge Cases).
 *
 * <p>Role/permission <em>definitions</em> (create/update/delete) are out of
 * scope for v1 — they change only through seed + Flyway. This service therefore
 * has no mutation path and writes no audit rows (reads are not audited, matching
 * {@code grantable-roles}).
 */
@Service
@RequiredArgsConstructor
public class RoleCatalogQueryService {

    /** Advertised on every catalog response so the console cannot misread it as tenant-scoped. */
    public static final String GLOBAL_SCOPE = "global";

    private final RolePermissionCatalogPort catalogPort;

    /** All roles + each role's permission-key set, stable seed (role-id) order. */
    public List<RolePermissionCatalogPort.RoleWithPermissions> listRoles() {
        return catalogPort.findAllRolesWithPermissions();
    }

    /**
     * The full permission-key catalog (rbac.md § Permission Keys). Sourced from
     * the code-canonical {@link Permission#catalog()} rather than the seed rows,
     * so a defined-but-ungranted key never goes missing (task Edge Cases).
     */
    public List<String> listPermissions() {
        return Permission.catalog();
    }
}
