package com.example.admin.domain.rbac;

/**
 * TASK-BE-631 (ADR-MONO-083 D4) — the {@code source_key} vocabulary of {@code operator_domain_set_grant}: the source
 * is part of the primary key, so the same (operator, tenant, set) granted by several sources is several rows and
 * every revoke deletes its own source's rows only (admin-api.md § 출처와 회수 규칙).
 *
 * <p>Framework-free.
 */
public final class DomainGrantSource {

    /** {@code POST /operators/{id}/domain-permission-sets}. Removed by the personal revoke and by the company exit. */
    public static final String DIRECT = "DIRECT";
    /** The V0052 backfill ({@code legacy-compat} only). Removed by the personal revoke and by the company exit. */
    public static final String MIGRATION = "MIGRATION";
    /** Prefix of a group fan-out row: {@code GROUP:<operator_group.id>}. Removed only through that group. */
    public static final String GROUP_PREFIX = "GROUP:";

    private DomainGrantSource() {
    }

    /** @return {@code GROUP:<groupInternalId>}. */
    public static String group(long groupInternalId) {
        return GROUP_PREFIX + groupInternalId;
    }

    /** @return {@code true} for a {@code GROUP:<id>} key. */
    public static boolean isGroup(String sourceKey) {
        return sourceKey != null && sourceKey.startsWith(GROUP_PREFIX);
    }

    /** @return the wire {@code source} of a row: {@code DIRECT} · {@code MIGRATION} · {@code GROUP}. */
    public static String wireSource(String sourceKey) {
        return isGroup(sourceKey) ? "GROUP" : sourceKey;
    }
}
