package com.example.order.domain.seller;

/**
 * Holds the current request's <b>seller scope</b> for the duration of request
 * processing — the inner marketplace axis nested under {@code tenant_id}
 * (ADR-MONO-030 Step 3 §3.3; ABAC {@code org_scope} shape, ADR-MONO-025). An
 * OPERATOR token may carry a seller-scope claim that the gateway forwards as the
 * {@code X-Seller-Scope} header; {@code SellerScopeContextFilter} reads it into
 * this holder, and the persistence layer consumes {@link #currentSellerScope()} /
 * {@link #isRestricted()} to (optionally) narrow OPERATOR order reads to orders
 * containing a line attributed to that seller — always <em>inside</em> the tenant
 * filter (isolate-then-attribute, AC-6).
 *
 * <p>Framework-free on purpose (a plain {@link ThreadLocal}).
 *
 * <p><b>net-zero / fail-OPEN (ADR-025 core invariant, F1):</b> when no seller
 * scope is bound — a standalone deployment, a CONSUMER request (the gateway never
 * forwards the header to the buyer plane, F5), a tenant operator with no seller
 * restriction, the saga/background path, or a unit test — or when the scope is the
 * wildcard {@code '*'}, {@link #isRestricted()} is {@code false} and reads return
 * the <em>full tenant view</em>. The seller axis NEVER fail-closes to empty.
 */
public final class SellerScopeContext {

    /** Wildcard scope: explicitly unrestricted (tenant-operator-wide view). */
    public static final String WILDCARD = "*";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SellerScopeContext() {
    }

    /**
     * Binds the seller scope for the current thread. Blank/null/{@code '*'} clears
     * the binding (→ unrestricted, net-zero).
     */
    public static void set(String sellerScope) {
        if (sellerScope == null || sellerScope.isBlank() || WILDCARD.equals(sellerScope.trim())) {
            CURRENT.remove();
        } else {
            CURRENT.set(sellerScope.trim());
        }
    }

    /**
     * The seller this operation is restricted to, or {@code null} when unrestricted
     * (net-zero). Pair with {@link #isRestricted()} for a single-branch repo filter.
     */
    public static String currentSellerScope() {
        return CURRENT.get();
    }

    /**
     * {@code true} only when a concrete (non-wildcard) seller scope is bound — i.e.
     * the read must restrict to orders attributed to that seller. {@code false} for
     * absent/blank/{@code '*'} (fail-OPEN, full tenant view).
     */
    public static boolean isRestricted() {
        String s = CURRENT.get();
        return s != null && !s.isBlank();
    }

    /** Clears the binding — MUST be called in a {@code finally} per request. */
    public static void clear() {
        CURRENT.remove();
    }
}
