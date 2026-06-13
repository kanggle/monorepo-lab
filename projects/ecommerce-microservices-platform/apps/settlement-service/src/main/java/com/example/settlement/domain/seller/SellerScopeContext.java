package com.example.settlement.domain.seller;

/**
 * Holds the current request's <b>seller scope</b> — the inner marketplace axis
 * nested under {@code tenant_id} (ADR-MONO-030 Step 3 §3.3; ABAC {@code org_scope}
 * shape, ADR-MONO-025). An OPERATOR token may carry a seller-scope claim that the
 * gateway forwards as {@code X-Seller-Scope}; {@code SellerScopeContextFilter} reads
 * it here, and the accrual repository consumes {@link #currentSellerScope()} /
 * {@link #isRestricted()} to narrow OPERATOR reads to one seller — always
 * <em>inside</em> the tenant filter (isolate-then-attribute, AC-8).
 *
 * <p>Framework-free on purpose (a plain {@link ThreadLocal}).
 *
 * <p><b>net-zero / fail-OPEN (ADR-025 core invariant, AC-8):</b> when no seller
 * scope is bound — a tenant operator with no restriction, a standalone deployment,
 * the consumer/background path, or a unit test — or when the scope is the wildcard
 * {@code '*'}, {@link #isRestricted()} is {@code false} and reads return the full
 * tenant view. The seller axis NEVER fail-closes to empty.
 */
public final class SellerScopeContext {

    /** Wildcard scope: explicitly unrestricted (tenant-operator-wide view). */
    public static final String WILDCARD = "*";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SellerScopeContext() {
    }

    /** Binds the seller scope. Blank/null/{@code '*'} clears it (→ unrestricted). */
    public static void set(String sellerScope) {
        if (sellerScope == null || sellerScope.isBlank() || WILDCARD.equals(sellerScope.trim())) {
            CURRENT.remove();
        } else {
            CURRENT.set(sellerScope.trim());
        }
    }

    /** The seller this read is restricted to, or {@code null} when unrestricted. */
    public static String currentSellerScope() {
        return CURRENT.get();
    }

    /** {@code true} only when a concrete (non-wildcard) seller scope is bound. */
    public static boolean isRestricted() {
        String s = CURRENT.get();
        return s != null && !s.isBlank();
    }

    /** Clears the binding — MUST be called in a {@code finally} per request. */
    public static void clear() {
        CURRENT.remove();
    }
}
