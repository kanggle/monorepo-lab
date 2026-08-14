package com.wms.admin.application.security;

/**
 * Which rows the current caller may read from the <b>tenant-owned</b> projections
 * (TASK-BE-583 / ADR-MONO-065 § D1).
 *
 * <p>Two states, and only two:
 *
 * <ul>
 *   <li><b>unrestricted</b> — the native wms operator plane and every internal flow
 *       (no security context). Sees every row, including rows with no tenant.</li>
 *   <li><b>restricted to {@code tenantId}</b> — a customer tenant admitted through the
 *       {@code entitled_domains} dual-accept. Sees only its own rows; rows with no
 *       tenant are invisible to it.</li>
 * </ul>
 *
 * <h2>Scope is only two of the eight dashboards</h2>
 *
 * <p>This applies to the order and shipment projections and nothing else. The other
 * six dashboards (inventory, throughput, ASN, adjustments, alerts, master refs) are
 * warehouse-global by decision (ADR-MONO-065 § D3, rider R1=a) — and could not be
 * scoped even if that decision went the other way, because nothing upstream of them
 * carries an owner: across the six wms databases (91 tables) the only tenant column
 * is {@code outbound_order.tenant_id}. A filter with no upstream source is a constant
 * comparison — permanently green, protecting nothing.
 *
 * <h2>Why this mirrors outbound-service's {@code CallerScope} rather than reusing it</h2>
 *
 * <p>Same predicate, deliberately duplicated: the two services share no module, and
 * {@code CallerScope} additionally carries the write-side {@code requireOrderAccess}
 * gate that has no meaning for a read-only projection. What must stay in step is the
 * <em>predicate</em> — signed claim only, native tenant is unrestricted, no wildcard
 * branch — not the code. {@link ReadScopeProvider} implementations pin it.
 */
public final class ReadScope {

    private static final ReadScope UNRESTRICTED = new ReadScope(null);

    private final String tenantId;

    private ReadScope(String tenantId) {
        this.tenantId = tenantId;
    }

    /** Sees every row, tenant-bearing or not. */
    public static ReadScope unrestricted() {
        return UNRESTRICTED;
    }

    /**
     * Sees only rows carrying {@code tenantId}.
     *
     * @throws IllegalArgumentException if {@code tenantId} is null or blank — that
     *     would silently degrade to {@link #unrestricted()} through
     *     {@link #tenantFilter()} and hand a caller every tenant's rows. Fail loudly
     *     instead: the only legitimate route to unrestricted is the named factory.
     */
    public static ReadScope restrictedTo(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException(
                    "restrictedTo requires a tenant; use unrestricted() explicitly");
        }
        return new ReadScope(tenantId);
    }

    public boolean isRestricted() {
        return tenantId != null;
    }

    /** The tenant this scope is pinned to, or {@code null} when unrestricted. */
    public String tenantId() {
        return tenantId;
    }

    /**
     * The value to bind to the repositories' {@code :tenantId} parameter.
     *
     * <p>{@code null} for an unrestricted caller, which the queries' standing
     * {@code (:tenantId IS NULL OR e.tenantId = :tenantId)} idiom reads as "no
     * filter" — the same shape the other optional filters in those queries already
     * use, and the same one {@code outbound-service}'s {@code OrderJpaRepository}
     * uses for this very axis.
     */
    public String tenantFilter() {
        return tenantId;
    }
}
