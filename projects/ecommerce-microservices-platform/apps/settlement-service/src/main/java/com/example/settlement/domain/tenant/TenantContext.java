package com.example.settlement.domain.tenant;

/**
 * Holds the current request's tenant id for the duration of request/message
 * processing (ADR-MONO-030 §2.2 M2 layer 2 — context propagation). The gateway has
 * verified the {@code tenant_id} claim and forwards it as {@code X-Tenant-Id};
 * {@code TenantContextFilter} reads it into this holder, and the persistence layer
 * consumes {@link #currentTenant()} to scope every read.
 *
 * <p><b>★ settlement note.</b> Unlike order/product, settlement does <em>not</em>
 * derive a write-path tenant from this context: an accrual's {@code tenant_id} comes
 * authoritatively from the cached {@code OrderPlaced} snapshot (the payment event
 * envelope carries no tenant — AC-7). This holder scopes only the HTTP <em>read</em>
 * path (and the rate-admin path).
 *
 * <p>Framework-free on purpose (a plain {@link ThreadLocal}).
 *
 * <p><b>net-zero / standalone (D8):</b> an unset context resolves to
 * {@link #DEFAULT_TENANT_ID}, so a single-store deployment behaves byte-identically
 * to its pre-multi-tenant self.
 */
public final class TenantContext {

    /** Default tenant the original single-store data is backfilled to. */
    public static final String DEFAULT_TENANT_ID = "ecommerce";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Binds the tenant for the current thread. Blank/null clears (→ default). */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId.trim());
        }
    }

    /**
     * The tenant scoping the current read. Never null: an unset context resolves to
     * {@link #DEFAULT_TENANT_ID}.
     */
    public static String currentTenant() {
        String t = CURRENT.get();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT_ID : t;
    }

    /** Clears the binding — MUST be called in a {@code finally} per request. */
    public static void clear() {
        CURRENT.remove();
    }
}
