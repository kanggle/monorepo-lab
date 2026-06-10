package com.example.account.domain.tenant;

import java.util.Set;

/**
 * TASK-BE-341 (ADR-MONO-023 D1): lifecycle status of a tenant↔domain
 * <em>subscription</em> (the entitlement-plane record), distinct from the tenant
 * aggregate's own {@link TenantStatus} lifecycle.
 *
 * <p>States:
 * <ul>
 *   <li>{@code PENDING} — provisioned but not yet activated (e.g. awaiting an
 *       out-of-band confirmation). Not entitled.</li>
 *   <li>{@code ACTIVE} — entitled; the tenant may use the domain. The catalog
 *       (ADR-019 D4) and the {@code entitled_domains} claim (ADR-019 D5 /
 *       ADR-020 D3) project ONLY this state.</li>
 *   <li>{@code SUSPENDED} — the generic "entitled-but-blocked" state any driver
 *       (admin, compliance, future billing — ADR-023 D5) sets; reversible. The
 *       domain drops from the catalog + next-issued {@code entitled_domains}, but
 *       IAM bindings (operator assignments + RBAC) are preserved (ADR-023 D2 —
 *       GCP billing↔IAM parity).</li>
 *   <li>{@code CANCELLED} — terminal. The subscription is permanently ended.</li>
 * </ul>
 *
 * <p>{@code SUSPENDED} is intentionally driver-agnostic — billing sub-states
 * (e.g. {@code PAST_DUE}) are NOT modelled here (deferred to the future billing
 * axis, ADR-023 D5).
 *
 * <p>Legal transitions (the state machine guard, ADR-023 D1):
 * <pre>
 *   PENDING   → ACTIVE | CANCELLED
 *   ACTIVE    → SUSPENDED | CANCELLED
 *   SUSPENDED → ACTIVE | CANCELLED
 *   CANCELLED → (terminal — no outgoing transition)
 * </pre>
 *
 * <p>Self-transitions ({@code X → X}) are NOT legal here; callers treat
 * "current == target" as an idempotent no-op rather than a transition.
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    CANCELLED;

    /**
     * Returns {@code true} when this is the entitled state ({@link #ACTIVE}).
     * The catalog + {@code entitled_domains} read paths filter on this.
     */
    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * Returns {@code true} when this is a terminal state with no legal outgoing
     * transition ({@link #CANCELLED}).
     */
    public boolean isTerminal() {
        return this == CANCELLED;
    }

    /**
     * Returns {@code true} when a transition from this state to {@code target} is
     * legal per the ADR-023 D1 state machine. Self-transitions return
     * {@code false} (callers treat current == target as an idempotent no-op).
     */
    public boolean canTransitionTo(SubscriptionStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == ACTIVE || target == CANCELLED;
            case ACTIVE -> target == SUSPENDED || target == CANCELLED;
            case SUSPENDED -> target == ACTIVE || target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    /**
     * The set of states from which a subscription is considered usable for a
     * brand-new "subscribe" (create) operation's resulting state. A freshly
     * created subscription lands in {@link #ACTIVE} (or {@link #PENDING} when an
     * activation step is required); never directly {@code SUSPENDED}/{@code CANCELLED}.
     */
    public static Set<SubscriptionStatus> creatable() {
        return Set.of(PENDING, ACTIVE);
    }
}
