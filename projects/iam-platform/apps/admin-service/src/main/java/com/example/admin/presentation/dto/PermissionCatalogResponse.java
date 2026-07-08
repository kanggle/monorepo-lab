package com.example.admin.presentation.dto;

import java.util.List;

/**
 * TASK-BE-486 — response for {@code GET /api/admin/permissions}: the full
 * permission-key catalog (rbac.md § Permission Keys), in the canonical rbac.md
 * order.
 *
 * <p>{@code scope} is always {@code "global"} for the same reason as
 * {@link RoleCatalogResponse} — permission keys are a platform-wide definition,
 * not tenant-scoped.
 */
public record PermissionCatalogResponse(String scope, List<String> permissions) {
}
