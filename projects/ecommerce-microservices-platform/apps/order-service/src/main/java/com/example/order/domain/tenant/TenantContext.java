package com.example.order.domain.tenant;

/**
 * Holds the current request's tenant id for the duration of request processing
 * (ADR-MONO-030 §2.2 M2 layer 2 — context propagation). The gateway has already
 * verified the {@code tenant_id} claim and forwards it as the {@code X-Tenant-Id}
 * header; {@code TenantContextFilter} reads that header into this holder, and the
 * persistence + event layers consume {@link #currentTenant()} to scope every read
 * and stamp every write/event.
 *
 * <p>Framework-free on purpose (a plain {@link ThreadLocal}): the domain event
 * factory and infrastructure repositories both depend on it without dragging in
 * Spring or servlet types. The async saga path (Kafka consumers) binds it from
 * the consumed event envelope, mirroring how the HTTP filter binds it from the
 * request header.
 *
 * <p><b>net-zero / standalone (D8):</b> when no tenant context is set — a
 * standalone deployment without the platform IAM, a background/reconciliation
 * thread, or a unit test — {@link #currentTenant()} resolves to
 * {@link #DEFAULT_TENANT_ID}, so the service behaves byte-identically to its
 * pre-multi-tenant single-store self.
 */
public final class TenantContext {

    /** Default tenant the original single-store data is backfilled to (V8). */
    public static final String DEFAULT_TENANT_ID = "ecommerce";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Binds the tenant for the current thread. Blank/null clears (→ default). */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            CURRENT.remove();
        } else {
            CURRENT.set(tenantId);
        }
    }

    /**
     * The tenant scoping the current operation. Never null: an unset context
     * (standalone / background / test) resolves to {@link #DEFAULT_TENANT_ID}.
     */
    public static String currentTenant() {
        String t = CURRENT.get();
        return (t == null || t.isBlank()) ? DEFAULT_TENANT_ID : t;
    }

    /** Clears the binding — MUST be called in a {@code finally} per request/message. */
    public static void clear() {
        CURRENT.remove();
    }
}
