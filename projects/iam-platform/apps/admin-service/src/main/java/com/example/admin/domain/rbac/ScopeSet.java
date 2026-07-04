package com.example.admin.domain.rbac;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * TASK-BE-477 / ADR-MONO-045 D3 — the {@code {domains}×{roles}} value object that
 * carries a cross-org partnership's {@code delegated_scope} / {@code participant_scope}
 * and the request-time derived cross-org scope (rbac.md "Cross-Org Partner Delegation
 * Confinement").
 *
 * <p>Framework-free domain value object (no Spring / JPA / Jackson imports — the
 * persistence JSON mapping lives on the infrastructure
 * {@code PartnershipScopeJson} adapter). Normalises on construction: each element is
 * trimmed, blanks dropped, duplicates collapsed to first occurrence, order preserved.
 *
 * <p>{@link #intersect(ScopeSet)} computes the element-wise intersection independently
 * on {@code domains} and {@code roles}, preserving {@code this}'s ordering — the
 * triple-intersection cap ({@code delegated ∩ participant ∩ host-holds}) is composed
 * from it.
 *
 * <p>{@link #containsAdminRole()} enforces the ADR-045 D3 data-model invariant that a
 * {@code delegated_scope} may NEVER carry an admin role — a partnership widens a
 * partner operator's domain-operating reach, never its admin scope.
 */
public final class ScopeSet {

    /**
     * Role names that are structurally forbidden from any {@code delegated_scope}
     * (ADR-045 D3 cap). A partnership can never delegate administrative authority
     * across the org boundary.
     */
    public static final Set<String> ADMIN_ROLE_NAMES =
            Set.of("SUPER_ADMIN", "TENANT_ADMIN", "TENANT_BILLING_ADMIN");

    private final List<String> domains;
    private final List<String> roles;

    private ScopeSet(List<String> domains, List<String> roles) {
        this.domains = domains;
        this.roles = roles;
    }

    /**
     * Constructs a normalised scope. {@code null} collections are treated as empty.
     */
    public static ScopeSet of(Collection<String> domains, Collection<String> roles) {
        return new ScopeSet(normalize(domains), normalize(roles));
    }

    /** The empty scope ({@code {domains:[], roles:[]}}). */
    public static ScopeSet empty() {
        return new ScopeSet(List.of(), List.of());
    }

    private static List<String> normalize(Collection<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                seen.add(t);
            }
        }
        return List.copyOf(seen);
    }

    public List<String> domains() {
        return domains;
    }

    public List<String> roles() {
        return roles;
    }

    /**
     * Element-wise intersection ({@code this ∩ other}) on {@code domains} and
     * {@code roles} independently. The result preserves {@code this}'s ordering and
     * only retains elements also present in {@code other}.
     */
    public ScopeSet intersect(ScopeSet other) {
        if (other == null) {
            return this;
        }
        Set<String> otherDomains = new LinkedHashSet<>(other.domains);
        Set<String> otherRoles = new LinkedHashSet<>(other.roles);
        List<String> d = new ArrayList<>();
        for (String x : domains) {
            if (otherDomains.contains(x)) {
                d.add(x);
            }
        }
        List<String> r = new ArrayList<>();
        for (String x : roles) {
            if (otherRoles.contains(x)) {
                r.add(x);
            }
        }
        return new ScopeSet(List.copyOf(d), List.copyOf(r));
    }

    /**
     * @return {@code true} iff any role is an admin role
     *         ({@link #ADMIN_ROLE_NAMES}) — the ADR-045 D3 invite-time cap.
     */
    public boolean containsAdminRole() {
        for (String role : roles) {
            if (ADMIN_ROLE_NAMES.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return {@code true} iff {@code this ⊆ other} (both {@code domains} and
     *         {@code roles} are subsets of {@code other}'s). Backs the
     *         {@code participant_scope ⊆ delegated_scope} validation.
     */
    public boolean isSubsetOf(ScopeSet other) {
        if (other == null) {
            return domains.isEmpty() && roles.isEmpty();
        }
        return other.domains.containsAll(domains) && other.roles.containsAll(roles);
    }

    public boolean isEmpty() {
        return domains.isEmpty() && roles.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScopeSet other)) {
            return false;
        }
        return domains.equals(other.domains) && roles.equals(other.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domains, roles);
    }

    @Override
    public String toString() {
        return "ScopeSet{domains=" + domains + ", roles=" + roles + '}';
    }
}
