package com.example.erp.masterdata.infrastructure.authorization;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.security.oauth2.TenantClaimValidator;
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

    /** TASK-MONO-793 — the ERP business roles (erp iam-integration.md § 업무 역할) and the derived entry role. */
    static final String ROLE_MASTERDATA_READ = "ERP_MASTERDATA_READ";
    static final String ROLE_MASTERDATA_WRITE = "ERP_MASTERDATA_WRITE";
    static final String ROLE_APPROVAL_READ = "ERP_APPROVAL_READ";
    static final String ROLE_APPROVAL_WRITE = "ERP_APPROVAL_WRITE";
    static final String ROLE_ENTRY = "ERP_OPERATOR";
    static final List<String> ERP_BUSINESS_ROLES = List.of(
            ROLE_MASTERDATA_READ, ROLE_MASTERDATA_WRITE, ROLE_APPROVAL_READ, ROLE_APPROVAL_WRITE);

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
        // Role check — TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q1–Q3, 2026-10-15 UTC; erp
        // iam-integration.md § 업무 역할). Business roles decide; the entry role ERP_OPERATOR (derived by
        // auth-service for every ERP set holder) opens only SELF. Two clauses are GONE on purpose:
        //  - entitled_domains ∋ erp ⇒ READ (ADR-MONO-019 § D5 authority-layer dual-accept): every assume token
        //    carries the tenant's subscriptions, so it opened masterdata to operators holding no ERP set at all
        //    (Q1). The TENANT gate's dual-accept (ServiceLevelOAuth2Config) is untouched — that one decides
        //    which tenant a token may enter, not what it may do there.
        //  - isOperator() (ERP_OPERATOR ∨ ERP_ADMIN ∨ SUPER_ADMIN) ⇒ READ/WRITE: ERP_OPERATOR is now carried by
        //    the approval-only operator too, and the other two are issued nowhere (ADR-033 S2 · ADR-083 D2).
        // The workload scopes erp.read / erp.write keep their arms (erp-platform-internal-services-client).
        boolean wildcard = TenantClaimValidator.WILDCARD_TENANT.equals(actor.tenantId());
        boolean masterdataRead = actor.hasRole(ROLE_MASTERDATA_READ) || actor.hasRole(ROLE_MASTERDATA_WRITE)
                || actor.hasScope(SCOPE_READ) || actor.hasScope(SCOPE_WRITE)
                // READ-only platform-super-admin wildcard admission (TASK-ERP-BE-031, unchanged): the
                // platform-console super-admin's base token — tenant_id='*', no erp scope, no domain roles.
                // Widens READ only; the WRITE arm below is never widened.
                || wildcard;
        boolean roleOk = switch (required) {
            case READ -> masterdataRead;
            // Q3 — approval-service forwards the submitter's token to resolve the approval subject.
            case SUBJECT_READ -> masterdataRead || actor.hasRole(ROLE_APPROVAL_WRITE);
            // Q2 — «about me», not masterdata: any ERP business role, the entry role, an erp scope, or '*'.
            case SELF -> wildcard || actor.hasScope(SCOPE_READ) || actor.hasScope(SCOPE_WRITE)
                    || actor.hasRole(ROLE_ENTRY) || ERP_BUSINESS_ROLES.stream().anyMatch(actor::hasRole);
            case WRITE -> actor.hasRole(ROLE_MASTERDATA_WRITE) || actor.hasScope(SCOPE_WRITE);
        };
        if (!roleOk) {
            return AuthorizationDecision.denyRole(
                    "actor lacks the erp role required for " + required.name().toLowerCase());
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
