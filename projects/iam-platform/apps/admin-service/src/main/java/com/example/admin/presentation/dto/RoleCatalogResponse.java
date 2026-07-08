package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-BE-486 — response for {@code GET /api/admin/roles}: every seed role with
 * its permission-key set, in stable seed (role-id) order.
 *
 * <p>{@code scope} is always {@code "global"} — the RBAC catalog is platform-wide
 * (no tenant column on {@code admin_roles}); the console must not filter it as
 * tenant-scoped data. This is the same table the 「권한 세트」 screen
 * (TASK-PC-FE-228) consumes, framed as roles carrying permission sets.
 */
public record RoleCatalogResponse(String scope, List<RoleItem> roles) {

    /** One {@code admin_roles} row + its bound permission keys (sorted ascending). */
    public record RoleItem(
            long id,
            String name,
            String description,
            List<String> permissions) {
    }
}
