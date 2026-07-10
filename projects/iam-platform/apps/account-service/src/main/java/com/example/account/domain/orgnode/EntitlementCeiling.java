package com.example.account.domain.orgnode;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * TASK-BE-491 (ADR-MONO-047 § D2/D3): the deny-only entitlement ceiling carried by
 * an {@link OrgNode}.
 *
 * <p>A ceiling is a <b>maximum</b> set of domain keys. It can only <b>narrow</b> what a
 * tenant may subscribe to or resolve — it never grants (D2-A, AWS SCP parity). The
 * effective ceiling at a tenant is the intersection down the node chain
 * {@code root → … → tenant.org_node}.
 *
 * <p><b>{@code UNBOUNDED} and {@code BOUNDED(∅)} are opposites, not synonyms.</b>
 * <ul>
 *   <li>{@link #unbounded()} — <i>no ceiling</i>. It is the <b>identity element</b> of
 *       {@link #intersect(EntitlementCeiling)} and the top element of
 *       {@link #isSubsetOf(EntitlementCeiling)}. Every domain is permitted.</li>
 *   <li>{@code bounded(∅)} — <b>nothing</b> is permitted. A tenant under such a node
 *       resolves zero entitled domains and its gateway 403s. That is the intended
 *       fail-closed direction, not a bug.</li>
 * </ul>
 *
 * <p><b>{@code UNBOUNDED} must never be modelled as "the set of all currently-known
 * domains".</b> A domain added later would then be silently excluded from every legacy
 * node — a bug that would surface months afterwards as "why can't acme-corp subscribe to
 * the new domain". The distinct {@link Mode#UNBOUNDED} case is the guard against that,
 * and it is pinned by {@code EntitlementCeilingTest}.
 *
 * <p>Immutable. Domain keys are stored in a canonical sorted order so the persisted CSV
 * is deterministic; membership — not order — is what the ceiling means.
 */
public record EntitlementCeiling(Mode mode, Set<String> domains) {

    /** A ceiling is either absent (identity) or an explicit maximum set. */
    public enum Mode {
        /** No ceiling. Intersection identity; permits every domain. */
        UNBOUNDED,
        /** An explicit maximum set. An EMPTY set permits nothing (fail-closed). */
        BOUNDED
    }

    private static final EntitlementCeiling UNBOUNDED_INSTANCE =
            new EntitlementCeiling(Mode.UNBOUNDED, Set.of());

    public EntitlementCeiling {
        if (mode == null) {
            throw new IllegalArgumentException("ceiling mode must not be null");
        }
        if (mode == Mode.UNBOUNDED && domains != null && !domains.isEmpty()) {
            // Canonical form: an UNBOUNDED ceiling carries no domain set. Allowing one
            // would create two encodings of the identity element.
            throw new IllegalArgumentException("UNBOUNDED ceiling must not carry domains");
        }
        domains = (domains == null) ? Set.of() : Set.copyOf(new TreeSet<>(domains));
    }

    /** The identity element: no ceiling. NOT "all known domains". */
    public static EntitlementCeiling unbounded() {
        return UNBOUNDED_INSTANCE;
    }

    /** An explicit maximum set. An empty collection means <b>nothing is permitted</b>. */
    public static EntitlementCeiling bounded(Collection<String> domainKeys) {
        return new EntitlementCeiling(Mode.BOUNDED, new LinkedHashSet<>(domainKeys));
    }

    public boolean isUnbounded() {
        return mode == Mode.UNBOUNDED;
    }

    /**
     * {@code this ∩ other}, with {@link #unbounded()} as the identity element.
     *
     * <p>Associative and commutative, so folding a node chain in either direction yields
     * the same effective ceiling.
     */
    public EntitlementCeiling intersect(EntitlementCeiling other) {
        if (other == null || other.isUnbounded()) {
            return this;
        }
        if (this.isUnbounded()) {
            return other;
        }
        Set<String> both = new LinkedHashSet<>(this.domains);
        both.retainAll(other.domains);
        return bounded(both);
    }

    /**
     * {@code this ⊆ other}. Used to enforce the D2 write invariant
     * {@code child.ceiling ⊆ parent.ceiling}.
     *
     * <p>{@code UNBOUNDED} is the top element: everything is a subset of it, and it is a
     * subset of nothing but itself. So a child may not be {@code UNBOUNDED} under a
     * {@code BOUNDED} parent — that would widen the child past its parent's bound.
     */
    public boolean isSubsetOf(EntitlementCeiling other) {
        if (other == null || other.isUnbounded()) {
            return true;
        }
        if (this.isUnbounded()) {
            return false;
        }
        return other.domains.containsAll(this.domains);
    }

    /** Whether {@code domainKey} is within this ceiling. */
    public boolean permits(String domainKey) {
        return isUnbounded() || domains.contains(domainKey);
    }

    // -- persistence encoding (org_node.ceiling_mode + org_node.ceiling_domains) ------

    /** CSV of the sorted domain keys; always {@code ""} when {@link #isUnbounded()}. */
    public String domainsCsv() {
        return String.join(",", domains);
    }

    /**
     * Rebuilds a ceiling from its two stored columns.
     *
     * <p>A {@code BOUNDED} row with an empty/blank CSV is the EMPTY set (nothing
     * permitted) — it must never be silently upgraded to {@link #unbounded()}.
     */
    public static EntitlementCeiling fromStorage(String ceilingMode, String ceilingDomainsCsv) {
        Mode parsed = Mode.valueOf(ceilingMode);
        if (parsed == Mode.UNBOUNDED) {
            return unbounded();
        }
        if (ceilingDomainsCsv == null || ceilingDomainsCsv.isBlank()) {
            return bounded(Set.of());
        }
        return bounded(java.util.Arrays.stream(ceilingDomainsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }
}
