package com.example.admin.domain.rbac;

import java.util.List;

/**
 * TASK-BE-631 (ADR-MONO-083) — the system preset keys of admin-api.md § 시스템 프리셋, in the table's order (the
 * order {@code GET /api/admin/domain-permission-sets} lists them in). The rows themselves — fixed {@code setId}s and
 * roles — are seeded by V0052 (15) and V0056 (the five SCM presets, TASK-MONO-792); {@code V0052MigrationShapeTest} /
 * {@code V0056MigrationShapeTest} pin each migration to its part of this list.
 *
 * <p>Framework-free.
 */
public final class DomainPermissionSetPresets {

    /** The backfill-only preset (MIGRATION source exclusively — never grantable through the API). */
    public static final String LEGACY_COMPAT = "legacy-compat";

    /** TASK-MONO-773 OD-4 — the company creator's set (source {@code ORIGIN}). */
    public static final String ALL_DOMAINS_FULL = "all-domains-full";

    /** Every preset key, in the contract's table order. */
    public static final List<String> SYSTEM_KEYS = List.of(
            "wms-inventory-read",
            "wms-inventory-operate",
            "wms-inbound-operate",
            "wms-outbound-operate",
            "wms-master-read",
            "wms-ops-actions",
            "wms-full",
            "scm-full",
            // TASK-MONO-792 (ADR-083 P5) — V0056: the SCM business presets, listed right after scm-full.
            "scm-read",
            "scm-procurement-operate",
            "scm-demand-planning-operate",
            "scm-logistics-operate",
            "scm-inventory-visibility-operate",
            "erp-full",
            "finance-full",
            "ecommerce-full",
            "fan-full",
            "mes-full",
            ALL_DOMAINS_FULL,
            LEGACY_COMPAT);

    private DomainPermissionSetPresets() {
    }

    /** @return the list position of {@code systemKey}; unknown keys sort last. */
    public static int orderOf(String systemKey) {
        int i = SYSTEM_KEYS.indexOf(systemKey);
        return i < 0 ? Integer.MAX_VALUE : i;
    }
}
