package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * v1 authorization adapter — derives role + data-scope from JWT claims
 * (architecture.md § Authorization matrix + Data scope, erp E6).
 *
 * <p><b>Fail-CLOSED defaults</b>:
 * <ul>
 *   <li>missing role-set / no recognised erp scope → {@code DENY_ROLE} →
 *       {@code PERMISSION_DENIED} (403).</li>
 *   <li>target department outside the actor's data-scope subtree →
 *       {@code DENY_SCOPE} → {@code DATA_SCOPE_FORBIDDEN} (403).</li>
 * </ul>
 *
 * <p><b>Data-scope = subtree containment (ADR-MONO-020 D3 amendment, 2026-06-05;
 * architecture.md § Authorization matrix point 3 v2).</b> {@code
 * actor.dataScopeDepartmentIds()} carries the operator's {@code org_scope} =
 * department <i>subtree-ROOT</i> ids (GAP injects roots only — it does not know
 * erp's department tree). The adapter expands each root → its descendants via
 * the {@link DepartmentRepository} hierarchy: a target is in-scope iff the
 * target itself OR any of its ancestors is one of the scoped roots. The walk
 * climbs the target's ancestor chain (O(depth), one walk per target — cheaper
 * than expanding every root's descendants) and terminates on the producer's
 * parent-cycle invariant (E1 {@code MASTERDATA_PARENT_CYCLE}), depth-bounded
 * defensively in {@link DepartmentRepository#ancestors(String, String)}.
 *
 * <p><b>NET-ZERO</b>: {@code "*"} (platform scope — machine tokens + unscoped
 * assignments) bypasses the containment check unchanged; an empty/absent
 * {@code org_scope} with a non-null target fails CLOSED unchanged. Only the
 * containment ALGORITHM changes (flat exact-match → subtree-aware), never which
 * target is passed.
 *
 * <p>v2 will swap this for a {@code permission-service} client behind the
 * same port without touching the application layer.
 */
@Component
public class RoleScopeAuthorizationAdapter implements AuthorizationPort {

    private static final String SCOPE_READ = "erp.read";
    private static final String SCOPE_WRITE = "erp.write";
    /** Domain key matched against the signed {@code entitled_domains} claim. */
    private static final String DOMAIN_KEY = "erp";

    /**
     * Department hierarchy port — resolves the target's ancestor chain for the
     * subtree-containment check. Nullable to keep the no-arg construction path
     * (legacy role/scope-only unit tests) working: when absent, an explicit
     * non-"*" org_scope falls back to flat exact-match (degraded but still
     * fail-closed), and "*" / empty / null-target paths are unaffected.
     */
    private final DepartmentRepository departmentRepository;

    public RoleScopeAuthorizationAdapter(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public AuthorizationDecision evaluate(ActorContext actor, RequiredScope required,
                                          String targetDepartmentId) {
        if (actor == null || actor.roles() == null) {
            return AuthorizationDecision.denyRole("no roles claim");
        }
        // Role check. READ additionally honours ADR-MONO-019 § D5
        // entitlement-trust: a signed entitled_domains ∋ "erp" claim grants
        // READ visibility even without an erp.read/operator role, mirroring the
        // tenant gate's TenantClaimValidator.isEntitled dual-accept. WRITE is
        // NEVER widened by entitlement-trust (visibility, not mutation).
        boolean roleOk = switch (required) {
            case READ -> actor.hasScope(SCOPE_READ) || actor.hasScope(SCOPE_WRITE)
                    || actor.isOperator() || actor.isEntitledTo(DOMAIN_KEY);
            case WRITE -> actor.hasScope(SCOPE_WRITE) || actor.isOperator();
        };
        if (!roleOk) {
            return AuthorizationDecision.denyRole(
                    "actor lacks required scope " + required.name().toLowerCase());
        }
        // Data-scope check (only if a target is supplied)
        if (targetDepartmentId == null) {
            return AuthorizationDecision.allow();
        }
        if (actor.isPlatformScope()) {
            return AuthorizationDecision.allow();
        }
        if (actor.dataScopeDepartmentIds() == null
                || actor.dataScopeDepartmentIds().isEmpty()) {
            // No explicit scope → fail-CLOSED for human operators. (Machine
            // tokens land here only if they failed to map to "*" — explicit
            // deny.) Covers the explicit empty org_scope=[] zero-scope case.
            return AuthorizationDecision.denyScope(
                    "actor has no data-scope and target is non-null");
        }
        // Subtree containment: the org_scope set holds department subtree-ROOT
        // ids; the target is in-scope iff it equals one of those roots OR is a
        // descendant of one. Climb the target's ancestor chain (target → … →
        // tree root) and accept on the first ancestor (incl. the target itself)
        // that is a scoped root.
        if (isWithinScopedSubtree(actor, targetDepartmentId)) {
            return AuthorizationDecision.allow();
        }
        return AuthorizationDecision.denyScope(
                "target department " + targetDepartmentId + " outside actor scope subtree");
    }

    /**
     * {@code true} iff {@code targetDepartmentId} is one of the actor's scoped
     * roots or a descendant of one. Walks the target's ancestor chain via the
     * department hierarchy (root-of-tree-bounded, cycle-free per E1). Falls back
     * to flat exact-match when the hierarchy port is unavailable (no-arg
     * construction) — still fail-closed (never widens scope beyond the roots).
     */
    private boolean isWithinScopedSubtree(ActorContext actor, String targetDepartmentId) {
        Set<String> scopedRoots = actor.dataScopeDepartmentIds();
        // Fast path / fail-soft when the hierarchy is not wired: exact-match on
        // the roots (the target IS one of the scoped roots). Never expands.
        if (scopedRoots.contains(targetDepartmentId)) {
            return true;
        }
        if (departmentRepository == null || actor.tenantId() == null) {
            return false;
        }
        // ancestors() returns [target, parent, …, tree-root] (depth-bounded,
        // cycle-broken). Any element that is a scoped root → in-scope.
        List<Department> chain = departmentRepository.ancestors(targetDepartmentId, actor.tenantId());
        for (Department d : chain) {
            if (scopedRoots.contains(d.getId())) {
                return true;
            }
        }
        return false;
    }
}
