package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.ScopeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * TASK-BE-327 / ADR-MONO-020 § 3.3 step 2 (D2) — resolves whether an operator
 * (identified by GAP OIDC subject) has the SELECTED customer tenant within their
 * effective tenant scope. Read-only authorization helper that backs the
 * auth-service assume-tenant exchange's fail-closed assignment gate
 * ({@code GET /internal/operator-assignments/check}).
 *
 * <p><b>Fail-closed read</b> (mirrors {@link TokenExchangeService} resolution
 * order): missing {@code admin_operators} row OR a non-{@code ACTIVE} operator →
 * {@code assigned=false}; never leaks operator existence vs unassigned beyond the
 * boolean.
 *
 * <p><b>Platform-scope sentinel</b> ({@code tenant_id == '*'}): a platform-scope
 * operator is assigned to any non-blank tenant ({@link AdminOperator#isPlatformScope()}
 * grants all tenants). The assumed token still carries the concrete selected
 * {@code tenant_id} — never the {@code '*'} sentinel (that resolution happens in
 * auth-service).
 *
 * <p>Otherwise the decision is delegated to {@link TenantScopeResolver} (BE-326
 * dual-read: assignment rows ∪ {legacy home tenant}).
 *
 * <p><b>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B) — account_id-only operator
 * resolution.</b> The Phase-2 transitional DUAL-KEY (account_id first, legacy email
 * fallback) is removed now that the part-A email→account_id backfill (TASK-MONO-298)
 * has migrated {@code admin_operators.oidc_subject} to the account UUID. The SAS
 * access-token {@code sub} IS the account UUID (jwt-standard-claims.md), so
 * {@link #check(String, String)} resolves the operator by it directly via the SHARED
 * {@link OperatorOidcSubjectResolver} (the same resolver the login-time exchange
 * uses). A row that does not match is {@code assigned=false} (fail-closed).
 *
 * <p><b>No {@code admin_actions} row</b> — read-only (ADR-014 token-exchange
 * "not audited" rule; the subsequent domain commands each audit). No
 * {@code @Transactional}: a fail-closed read only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorAssignmentCheckUseCase {

    private final TenantScopeResolver tenantScopeResolver;
    private final OperatorTenantAssignmentPort assignmentPort;
    // TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): the shared account_id-only resolver
    // — the SAME resolution the login-time exchange (TokenExchangeService) uses, so
    // the two sub-keyed paths cannot diverge.
    private final OperatorOidcSubjectResolver operatorResolver;
    // TASK-BE-477 (ADR-MONO-045 D3/D5): the cross-org partnership confinement inputs.
    private final TenantPartnershipPort partnershipPort;
    private final HostEntitledScopeResolver hostEntitledScopeResolver;

    /**
     * Boolean convenience kept for callers that need only the assignment verdict.
     *
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id)
     * @param tenantId    the selected (assume-target) customer tenant id
     * @return {@code true} iff the operator's effective tenant scope contains
     *         {@code tenantId}; {@code false} fail-closed otherwise
     */
    public boolean isAssigned(String oidcSubject, String tenantId) {
        return check(oidcSubject, tenantId).assigned();
    }

    /**
     * TASK-BE-338 / ADR-MONO-020 D3 amendment — resolves the assignment verdict
     * AND the per-assignment data-scope ({@code org_scope}) for the selected
     * tenant.
     *
     * <p>{@code orgScope} is the department subtree-root id list the operator may
     * act under within the selected tenant; {@code null} ⟺ {@code ["*"]} = whole
     * tenant (net-zero default — returned for an unset column, no explicit
     * assignment row, platform-scope, and every {@code assigned=false} case). The
     * auth-service customizer maps {@code null}/empty/absent → {@code ["*"]}.
     *
     * <p>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only operator
     * resolution — the operator row is resolved by the account_id {@code oidcSubject}
     * (the SAS access-token {@code sub}). See the class javadoc.
     *
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id) from the
     *                    validated subject token
     * @param tenantId    the selected (assume-target) customer tenant id
     * @return the assignment verdict + the selected assignment's {@code org_scope}
     *         ({@code null} when unset / not assigned)
     */
    public Result check(String oidcSubject, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            // Blank/malformed selected tenant never resolves to an assignment.
            return Result.notAssigned();
        }

        // 1. Resolve admin_operators row, FAIL-CLOSED, account_id-only (ADR-040
        // Phase 3 part B): the validated sub IS the account UUID and
        // admin_operators.oidc_subject is backfilled to account_id (part A).
        // Delegated to the SHARED OperatorOidcSubjectResolver so this path and the
        // login-time exchange (TokenExchangeService) cannot diverge.
        AdminOperatorPort.OperatorView operator =
                operatorResolver.resolve(oidcSubject).orElse(null);
        if (operator == null) {
            log.debug("assignment-check fail-closed: no admin_operators row for the OIDC subject "
                    + "(account_id)");
            return Result.notAssigned();
        }
        if (!"ACTIVE".equals(operator.status())) {
            log.debug("assignment-check fail-closed: operator status={} (not ACTIVE)", operator.status());
            return Result.notAssigned();
        }

        // 2. Platform-scope sentinel → assigned to any non-blank tenant. No
        // explicit assignment row, so org_scope defaults to null (→ ["*"]).
        if (AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId())) {
            return new Result(true, null, null);
        }

        // 3. Dual-read effective scope: assignment rows ∪ {legacy home tenant}.
        Set<String> effectiveScope = tenantScopeResolver.resolveEffectiveTenantScope(
                operator.internalId(), operator.tenantId());
        if (effectiveScope.contains(tenantId)) {
            // 4. Resolve the selected assignment's org_scope data-scope. null ⟺ ["*"]
            // (unset column OR legacy-home/platform with no explicit row). A normal
            // assignment carries NO delegatedScope block (partnership-only, additive).
            List<String> orgScope = assignmentPort.findOrgScope(operator.internalId(), tenantId);
            return new Result(true, orgScope, null);
        }

        // 5. TASK-BE-477 (ADR-MONO-045 D3/D5) — cross-org partnership branch (additive).
        // The operator is not normally assigned to `tenantId`; check whether an ACTIVE
        // partnership makes it reachable AS A HOST where the operator is a participant.
        // The derived reach is capped to the triple-intersection and returned as the
        // additive `delegatedScope` block (auth-service step 2b consumes it to cap the
        // assume-tenant token's entitled domains/roles; admin scope is NEVER widened).
        DelegatedScope delegated = resolveCrossOrgDelegatedScope(operator, tenantId);
        if (delegated != null) {
            return new Result(true, null, delegated);
        }
        return Result.notAssigned();
    }

    /**
     * TASK-BE-477 / ADR-MONO-045 D3/D5 — the cross-org confinement derivation
     * (rbac.md "Cross-Org Partner Delegation Confinement"):
     *
     * <pre>
     *   p = findActive(host = tenantId, partner = operator.tenantId)   // ACTIVE only
     *   if p is null: return null                                       // no reach
     *   part = participant(p, operator); if part is null: return null   // not a participant
     *   scope = p.delegated_scope
     *   if part.participant_scope != null: scope ∩= participant_scope
     *   scope ∩= hostEntitledScope(host)                                // ≤-own (deferred)
     *   return scope
     * </pre>
     *
     * @return the derived {@code {domains, roles}}, or {@code null} when the operator
     *         has no partnership-derived reach to {@code hostTenant} (fail-closed).
     */
    private DelegatedScope resolveCrossOrgDelegatedScope(AdminOperatorPort.OperatorView operator,
                                                         String hostTenant) {
        TenantPartnershipPort.PartnershipView partnership = partnershipPort
                .findActivePartnership(hostTenant, operator.tenantId())
                .orElse(null);
        if (partnership == null) {
            return null; // no partnership / PENDING / SUSPENDED / TERMINATED → reach 0
        }
        TenantPartnershipPort.ParticipantView participant = partnershipPort
                .findParticipant(partnership.internalId(), operator.internalId())
                .orElse(null);
        if (participant == null) {
            return null; // this operator is not a participant → reach 0
        }
        ScopeSet scope = partnership.delegatedScope();
        if (participant.participantScope() != null) {
            scope = scope.intersect(participant.participantScope());
        }
        Optional<ScopeSet> hostHolds = hostEntitledScopeResolver.resolve(hostTenant);
        if (hostHolds.isPresent()) {
            scope = scope.intersect(hostHolds.get());
        }
        return new DelegatedScope(scope.domains(), scope.roles());
    }

    /**
     * TASK-BE-338 — assignment-check result: the boolean verdict + the selected
     * assignment's {@code org_scope} ({@code null} ⟺ {@code ["*"]} net-zero).
     *
     * @param assigned whether the operator is assigned to the selected tenant
     * @param orgScope the selected assignment's department subtree-root ids, or
     *                 {@code null} when unset / not assigned (→ {@code ["*"]})
     */
    public record Result(boolean assigned, List<String> orgScope, DelegatedScope delegatedScope) {
        static Result notAssigned() {
            return new Result(false, null, null);
        }
    }

    /**
     * TASK-BE-477 / ADR-MONO-045 D3/D5 — the ADDITIVE cross-org confinement block:
     * the capped {@code {domains, roles}} a partnership-derived host tenant grants the
     * operator ({@code delegated_scope ∩ participant_scope ∩ host-holds}). Present ONLY
     * for partnership-derived host reach ({@code null} for normal assignments and all
     * {@code assigned=false} cases → omitted by {@code @JsonInclude(NON_NULL)}).
     * auth-service (step 2b) intersects the assume-tenant token's {@code entitled_domains}
     * with {@code domains} and caps roles to {@code roles}; admin scope is never widened.
     */
    public record DelegatedScope(List<String> domains, List<String> roles) {}
}
