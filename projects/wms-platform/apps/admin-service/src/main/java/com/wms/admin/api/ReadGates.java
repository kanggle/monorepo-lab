package com.wms.admin.api;

/**
 * The read gates of admin-service's dashboard and settings queries — TASK-MONO-827
 * (ADR-MONO-083 · owner decisions Q1/Q2/Q4, 2026-11-09 UTC).
 *
 * <p>Until TASK-MONO-827 every one of these read paths was {@code hasRole('WMS_VIEWER')}
 * and {@code SecurityConfig} synthesised {@code ROLE_WMS_VIEWER} for any token whose signed
 * {@code entitled_domains} contained {@code wms}. A subscription alone therefore opened every
 * dashboard — an operator with no business permission set at all read orders, shipments, ASNs
 * and stock, which is exactly the gap ERP (TASK-MONO-793 Q1) and Finance (TASK-MONO-794) closed.
 * The synthesis is gone (Q1) and each read is now decided by the WMS business role that owns
 * the data it shows (Q2), the same role the producing service itself checks:
 *
 * <ul>
 *   <li>orders · shipments → {@link #OUTBOUND} (outbound-service reads on {@code OUTBOUND_*});</li>
 *   <li>ASNs · inspections → {@link #INBOUND};</li>
 *   <li>inventory · adjustments → {@link #INVENTORY};</li>
 *   <li>master refs → {@link #MASTER};</li>
 *   <li>throughput · metrics · alert list · settings reads → {@link #ANY_WMS_BUSINESS_ROLE} —
 *       they aggregate across outbound, inbound and stock (or configure all of them), so no single
 *       area owns them (Q2 for the dashboards, Q4 for settings).</li>
 * </ul>
 *
 * <p><b>{@code WMS_VIEWER} stays in every gate on purpose.</b> Through admin-service's
 * {@code RoleHierarchy} ({@code SUPERADMIN > ADMIN > OPERATOR > VIEWER}) it is what lets a
 * {@code WMS_OPERATOR} holder ({@code wms-ops-actions} · {@code wms-full} · {@code legacy-compat})
 * keep reading everything it read before. It is no longer synthesised, and neither iam's
 * {@code DomainRoleCatalog} nor the issuer's wms ceiling contains it, so no operator token
 * carries it directly — the only ways in are a business role above or the hierarchy.
 *
 * <p>The {@code roles} claim is not domain-qualified, so these names must not collide with
 * another domain's role names. They do not: every other domain's catalog roles are prefixed
 * ({@code SCM_INVENTORY_VISIBILITY_READ}, {@code ERP_MASTERDATA_READ}, …), which the
 * «other-domain set only → 403» row of {@code DashboardBusinessRoleGateSliceTest} pins.
 */
public final class ReadGates {

    private static final String OUTBOUND_ROLES = "'OUTBOUND_READ','OUTBOUND_WRITE'";
    private static final String INBOUND_ROLES = "'INBOUND_READ','INBOUND_WRITE'";
    private static final String INVENTORY_ROLES = "'INVENTORY_READ','INVENTORY_WRITE'";
    private static final String MASTER_ROLES = "'MASTER_READ'";

    /** Outbound orders and shipments. */
    public static final String OUTBOUND = "hasAnyRole('WMS_VIEWER'," + OUTBOUND_ROLES + ")";

    /** ASNs and their inspections. */
    public static final String INBOUND = "hasAnyRole('WMS_VIEWER'," + INBOUND_ROLES + ")";

    /** Inventory snapshots and adjustment audit. */
    public static final String INVENTORY = "hasAnyRole('WMS_VIEWER'," + INVENTORY_ROLES + ")";

    /** Master-data reference lists. */
    public static final String MASTER = "hasAnyRole('WMS_VIEWER'," + MASTER_ROLES + ")";

    /** Cross-area reads: throughput, metrics, the alert list, settings. */
    public static final String ANY_WMS_BUSINESS_ROLE = "hasAnyRole('WMS_VIEWER',"
            + OUTBOUND_ROLES + "," + INBOUND_ROLES + "," + INVENTORY_ROLES + "," + MASTER_ROLES + ")";

    private ReadGates() {
    }
}
