package com.example.erp.readmodel.presentation.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The operator's {@code org_scope} read-narrowing scope (TASK-ERP-BE-008 /
 * ADR-MONO-020 D3 amendment). Carries department subtree-ROOT ids the operator
 * may see; the org-view query expands these → descendants for the read filter.
 *
 * <p>Three states, mirroring masterdata-service's write-side semantics:
 * <ul>
 *   <li>{@link #platform()} — {@code org_scope=["*"]} or absent → <b>no
 *       narrowing</b> (net-zero; every BE-007 caller is unaffected).</li>
 *   <li>{@link #of(Set)} with a non-empty root set → narrow to the union of
 *       those roots' subtrees.</li>
 *   <li>{@link #of(Set)} with an empty set (explicit {@code org_scope=[]}) →
 *       zero-scope: narrows to nothing (fail-closed empty result / 404).</li>
 * </ul>
 */
public final class OrgScope {

    private static final OrgScope PLATFORM = new OrgScope(true, Collections.emptySet());

    private final boolean platform;
    private final Set<String> roots;

    private OrgScope(boolean platform, Set<String> roots) {
        this.platform = platform;
        this.roots = roots;
    }

    /** Platform-wide / absent scope — no read narrowing (net-zero). */
    public static OrgScope platform() {
        return PLATFORM;
    }

    /** A bounded scope over the given subtree-root ids (may be empty = zero-scope). */
    public static OrgScope of(Set<String> roots) {
        return new OrgScope(false, roots == null
                ? Collections.emptySet() : new LinkedHashSet<>(roots));
    }

    /** {@code true} when the scope imposes no read narrowing (platform / absent). */
    public boolean isPlatform() {
        return platform;
    }

    /** The subtree-root ids (empty for platform scope and for explicit zero-scope). */
    public Set<String> roots() {
        return Collections.unmodifiableSet(roots);
    }
}
