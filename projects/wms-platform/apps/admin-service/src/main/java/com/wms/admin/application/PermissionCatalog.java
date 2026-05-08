package com.wms.admin.application;

import java.util.Set;

/**
 * Known permission strings accepted by {@code Role.permissionsJson}. Unknown
 * strings produce {@code SETTING_VALIDATION_ERROR} per
 * {@code admin-service-api.md § 3.1}. The list mirrors the seeded built-in
 * roles in V99__seed_dev_data.sql.
 */
public final class PermissionCatalog {

    public static final Set<String> KNOWN = Set.of(
            "INVENTORY_READ", "INVENTORY_WRITE",
            "INBOUND_READ", "INBOUND_WRITE",
            "OUTBOUND_READ", "OUTBOUND_WRITE",
            "MASTER_READ", "MASTER_WRITE",
            "ALERT_READ", "ALERT_ACKNOWLEDGE",
            "ADMIN_USER_WRITE", "ADMIN_ROLE_WRITE",
            "ADMIN_ASSIGNMENT_WRITE", "ADMIN_SETTINGS_WRITE",
            "ADMIN_FORCE_OVERRIDE"
    );

    private PermissionCatalog() {}

    public static boolean isKnown(String permission) {
        return KNOWN.contains(permission);
    }
}
