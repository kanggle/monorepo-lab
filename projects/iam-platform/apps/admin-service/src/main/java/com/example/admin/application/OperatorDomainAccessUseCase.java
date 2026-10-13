package com.example.admin.application;

import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GrantRowView;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.DomainGrantSource;
import com.example.admin.domain.rbac.DomainPermissionSetPresets;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.example.admin.domain.rbac.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-BE-631 (ADR-MONO-083 D4 · D5 · D9 · OD-3 · OD-4) — an operator's domain permission sets per tenant
 * (admin-api.md § 도메인 업무 권한 세트 — § 행동 테넌트 rows 26 and 28):
 * <ul>
 *   <li>{@code GET /operators/{operatorId}/domain-access} — effective work roles + the source of every set;</li>
 *   <li>{@code POST /operators/{operatorId}/domain-permission-sets} — a DIRECT grant;</li>
 *   <li>{@code DELETE /operators/{operatorId}/domain-permission-sets/{setId}} — the personal revoke;</li>
 *   <li>{@code GET /me/domain-access} — the caller's own value (no permission key).</li>
 * </ul>
 *
 * <p><b>Decision order of a grant</b> (the first failure answers, nothing is written):
 * <ol>
 *   <li>T (the acting tenant — {@code X-Tenant-Id} under the omitted-tenant rule) ∈ the {@code domain_access.manage}
 *       GRANT scope — else {@code 403 TENANT_SCOPE_DENIED} + DENIED row ({@link QueryTenantScopeGate});</li>
 *   <li>the target exists — else {@code 404 OPERATOR_NOT_FOUND};</li>
 *   <li>a {@code '*'} target needs the actor's {@code '*'} grant scope (OD-3 — the «행 전체 규칙» {@code '*'} row) —
 *       else {@code 403 TENANT_SCOPE_DENIED} + DENIED row; T may be concrete or {@code '*'}. A non-{@code '*'}
 *       target: T = {@code '*'} → {@code 400 VALIDATION_ERROR}; T not one of its memberships →
 *       {@code 404 OPERATOR_NOT_FOUND} (another company's membership is not disclosed);</li>
 *   <li>the set: missing · another tenant's · a tenant set on a {@code '*'} row → {@code 404
 *       DOMAIN_PERMISSION_SET_NOT_FOUND}; {@code legacy-compat} → {@code 400 VALIDATION_ERROR} (MIGRATION only);</li>
 *   <li>T ≠ {@code '*'}: the set's domains ⊆ T's entitled domains — {@code 422 DOMAIN_NOT_SUBSCRIBED} / {@code 503};</li>
 *   <li>the same (target, T, set, DIRECT) row exists → {@code 409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED}. A
 *       MIGRATION or GROUP row of the same set does not count — sources coexist.</li>
 * </ol>
 * A grant to oneself is allowed (OD-4) and marked {@code selfGrant=true} in the audit detail. Nothing here creates a
 * membership row (a {@code '*'} operator stays membership-free — ADR-082 D2), and nothing touches the
 * management-plane permission cache. Every change reaches tokens from the NEXT assume exchange only (D7 — and only
 * once P3 reads it).
 *
 * <p><b>Personal revoke</b> deletes the (target, T, set) rows of source DIRECT and MIGRATION only; GROUP rows stay
 * (a set received from a group is removed through that group). Nothing of those two sources → 404.
 */
@Service
@RequiredArgsConstructor
public class OperatorDomainAccessUseCase {

    private static final List<String> PERSONAL_SOURCES = List.of(DomainGrantSource.DIRECT, DomainGrantSource.MIGRATION);

    private final DomainPermissionSetPort setPort;
    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final QueryTenantScopeGate tenantGate;
    private final TenantScopeGuard tenantScopeGuard;
    private final DomainSetEntitlementGuard entitlementGuard;
    private final AdminActionAuditor auditor;

    // ── reads ───────────────────────────────────────────────────────────────

    /** {@code GET /operators/{operatorId}/domain-access} — no audit row (refusals write none either). */
    @Transactional(readOnly = true)
    public OperatorDomainAccessResult domainAccess(OperatorContext actor, String requestedTenantId,
                                                   String operatorId) {
        QueryTenantScopeGate.Resolved resolved =
                tenantGate.resolveWithoutDenyRow(actor, requestedTenantId, Permission.DOMAIN_ACCESS_MANAGE);
        String tenantId = resolved.tenantId();
        AdminOperatorPort.OperatorView target = requireOperator(operatorId);
        if (isPlatform(target)) {
            if (!resolved.isPlatformScope()) {
                throw new TenantScopeDeniedException(
                        "A '*' operator's domain access is visible to the '*' grant scope of domain_access.manage only");
            }
            return assemble(target, tenantId, platformRowTenants(tenantId));
        }
        if (isPlatformTenant(tenantId)) {
            // A non-'*' operator can hold no '*' row — the honest answer is «nothing here».
            return OperatorDomainAccessResult.empty(target.operatorId(), tenantId);
        }
        requireMembership(target, tenantId);
        return assemble(target, tenantId, List.of(tenantId));
    }

    /**
     * {@code GET /me/domain-access} — the caller's own value, no permission key, no tenant gate. A tenant the caller
     * is not a member of answers 200 with nothing (a question about oneself discloses nothing).
     */
    @Transactional(readOnly = true)
    public OperatorDomainAccessResult myDomainAccess(OperatorContext actor, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("X-Tenant-Id is required");
        }
        AdminOperatorPort.OperatorView self = requireOperator(actor == null ? null : actor.operatorId());
        if (isPlatform(self)) {
            return assemble(self, tenantId, platformRowTenants(tenantId));
        }
        if (isPlatformTenant(tenantId)
                || !assignmentPort.findAssignedTenantIds(self.internalId()).contains(tenantId)) {
            return OperatorDomainAccessResult.empty(self.operatorId(), tenantId);
        }
        return assemble(self, tenantId, List.of(tenantId));
    }

    // ── writes ──────────────────────────────────────────────────────────────

    @Transactional
    public GrantDomainPermissionSetResult grant(GrantDomainPermissionSetCommand cmd) {
        AuditReasons.require(cmd.reason());
        if (cmd.setId() == null || cmd.setId().isBlank()) {
            throw new IllegalArgumentException("setId is required");
        }
        Authorized authorized = authorizeTarget(cmd.actor(), cmd.requestedTenantId(),
                cmd.operatorId(), ActionCode.OPERATOR_DOMAIN_SET_GRANT);
        AdminOperatorPort.OperatorView target = authorized.target();
        String tenantId = authorized.tenantId();

        // 4. the set
        SetView set = setPort.findBySetId(cmd.setId()).orElseThrow(() -> setNotFound(cmd.setId()));
        if (!set.isPreset() && (isPlatformTenant(tenantId) || !set.tenantId().equals(tenantId))) {
            throw setNotFound(cmd.setId());
        }
        if (DomainPermissionSetPresets.LEGACY_COMPAT.equals(set.systemKey())) {
            throw new IllegalArgumentException(
                    "'legacy-compat' is granted by the migration only (MIGRATION source) — choose another set");
        }
        // 5. entitlement (a '*' row has none — auth-service's ∩ entitled filters it per tenant)
        if (!isPlatformTenant(tenantId)) {
            entitlementGuard.requireEntitled(tenantId, DomainRoleCatalog.domainsOf(set.roles()));
        }
        // 6. same source already there
        if (setPort.grantExists(target.internalId(), tenantId, set.internalId(), DomainGrantSource.DIRECT)) {
            throw new DomainPermissionSetAlreadyGrantedException(
                    "Set '" + set.setId() + "' is already granted DIRECT to this operator in '" + tenantId + "'");
        }

        Instant now = Instant.now();
        setPort.insertGrant(target.internalId(), tenantId, set.internalId(), DomainGrantSource.DIRECT, null,
                operatorPort.resolveActorInternalId(cmd.actor().operatorId()), now);
        boolean selfGrant = cmd.actor().operatorId().equals(target.operatorId());
        audit(ActionCode.OPERATOR_DOMAIN_SET_GRANT, cmd.actor(), target.operatorId(), tenantId, cmd.reason(),
                "setId=" + set.setId() + " source=" + DomainGrantSource.DIRECT + " selfGrant=" + selfGrant, now);
        return new GrantDomainPermissionSetResult(target.operatorId(), tenantId, set.setId(), DomainGrantSource.DIRECT,
                now, cmd.actor().operatorId(), selfGrant);
    }

    @Transactional
    public void revoke(RevokeDomainPermissionSetCommand cmd) {
        AuditReasons.require(cmd.reason());
        Authorized authorized = authorizeTarget(cmd.actor(), cmd.requestedTenantId(),
                cmd.operatorId(), ActionCode.OPERATOR_DOMAIN_SET_REVOKE);
        AdminOperatorPort.OperatorView target = authorized.target();
        String tenantId = authorized.tenantId();

        SetView set = setPort.findBySetId(cmd.setId()).orElseThrow(() -> setNotFound(cmd.setId()));
        List<String> removed = setPort.deleteGrants(target.internalId(), tenantId, set.internalId(), PERSONAL_SOURCES);
        if (removed.isEmpty()) {
            throw new DomainPermissionSetNotFoundException("Set '" + set.setId()
                    + "' has no DIRECT / MIGRATION grant for this operator in '" + tenantId
                    + "' — a set received from a group is removed through that group");
        }
        long keptGroupSources = setPort.findGrantRows(target.internalId(), List.of(tenantId)).stream()
                .filter(r -> r.set().internalId() == set.internalId() && DomainGrantSource.isGroup(r.sourceKey()))
                .count();
        Instant now = Instant.now();
        audit(ActionCode.OPERATOR_DOMAIN_SET_REVOKE, cmd.actor(), target.operatorId(), tenantId, cmd.reason(),
                "setId=" + set.setId() + " removedSources=" + String.join(",", removed)
                        + " keptGroupSources=" + keptGroupSources, now);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** The outcome of steps 1 – 3: the target operator and the acting tenant T. */
    private record Authorized(AdminOperatorPort.OperatorView target, String tenantId) {}

    /** Steps 1 – 3 of the grant order, shared with the revoke (the same predicate — «판정은 POST 의 1 ~ 3 과 같다»). */
    private Authorized authorizeTarget(OperatorContext actor, String requestedTenantId,
                                       String operatorId, ActionCode code) {
        QueryTenantScopeGate.Resolved resolved =
                tenantGate.resolve(actor, requestedTenantId, code, Permission.DOMAIN_ACCESS_MANAGE);
        String tenantId = resolved.tenantId();
        AdminOperatorPort.OperatorView target = requireOperator(operatorId);
        if (isPlatform(target)) {
            if (!resolved.isPlatformScope()) {
                tenantScopeGuard.recordTenantDenied(actor, Permission.DOMAIN_ACCESS_MANAGE, tenantId, code);
                throw new TenantScopeDeniedException(
                        "Only the '*' grant scope of domain_access.manage may change a '*' operator's sets");
            }
        } else {
            if (isPlatformTenant(tenantId)) {
                throw new IllegalArgumentException(
                        "A '*' (all tenants) set row is for '*' operators only — send the company in X-Tenant-Id");
            }
            requireMembership(target, tenantId);
        }
        return new Authorized(target, tenantId);
    }

    private void requireMembership(AdminOperatorPort.OperatorView target, String tenantId) {
        Set<String> memberships = assignmentPort.findAssignedTenantIds(target.internalId());
        if (!memberships.contains(tenantId)) {
            // Same answer as PATCH …/roles: another company's membership is not disclosed.
            throw new OperatorNotFoundException(
                    "Operator not found for operatorId=" + target.operatorId() + " in tenant " + tenantId);
        }
    }

    private AdminOperatorPort.OperatorView requireOperator(String operatorId) {
        if (operatorId == null || operatorId.isBlank()) {
            throw new OperatorNotFoundException("Operator not found");
        }
        return operatorPort.findByOperatorId(operatorId)
                .orElseThrow(() -> new OperatorNotFoundException("Operator not found for operatorId=" + operatorId));
    }

    /** The grant-row tenants a {@code '*'} operator's effective value reads: T and '*' (just '*' when T = '*'). */
    private static List<String> platformRowTenants(String tenantId) {
        return isPlatformTenant(tenantId)
                ? List.of(AdminOperator.PLATFORM_TENANT_ID)
                : List.of(tenantId, AdminOperator.PLATFORM_TENANT_ID);
    }

    private OperatorDomainAccessResult assemble(AdminOperatorPort.OperatorView operator, String tenantId,
                                                List<String> rowTenants) {
        List<GrantRowView> rows = new ArrayList<>(setPort.findGrantRows(operator.internalId(), rowTenants));
        rows.sort(Comparator.comparing(GrantRowView::grantedAt).thenComparing(GrantRowView::sourceKey));
        Map<Long, SetView> setsInOrder = new LinkedHashMap<>();
        Map<Long, List<OperatorDomainAccessResult.Source>> sourcesBySet = new LinkedHashMap<>();
        List<RoleRef> roles = new ArrayList<>();
        for (GrantRowView row : rows) {
            SetView set = row.set();
            if (setsInOrder.putIfAbsent(set.internalId(), set) == null) {
                roles.addAll(set.roles());
            }
            boolean group = DomainGrantSource.isGroup(row.sourceKey());
            sourcesBySet.computeIfAbsent(set.internalId(), k -> new ArrayList<>())
                    .add(new OperatorDomainAccessResult.Source(DomainGrantSource.wireSource(row.sourceKey()),
                            group ? row.groupId() : null, group ? row.groupName() : null,
                            row.tenantId(), row.grantedAt(), row.grantedByOperatorId()));
        }
        List<OperatorDomainAccessResult.SetAccess> sets = new ArrayList<>();
        for (SetView set : setsInOrder.values()) {
            sets.add(new OperatorDomainAccessResult.SetAccess(set.setId(), set.name(), set.systemKey(),
                    List.copyOf(sourcesBySet.get(set.internalId()))));
        }
        return new OperatorDomainAccessResult(operator.operatorId(), tenantId,
                DomainRoleCatalog.sortAndDistinct(roles), List.copyOf(sets));
    }

    private static boolean isPlatform(AdminOperatorPort.OperatorView operator) {
        return AdminOperator.PLATFORM_TENANT_ID.equals(operator.tenantId());
    }

    private static boolean isPlatformTenant(String tenantId) {
        return AdminOperator.PLATFORM_TENANT_ID.equals(tenantId);
    }

    private static DomainPermissionSetNotFoundException setNotFound(String setId) {
        return new DomainPermissionSetNotFoundException("Domain permission set '" + setId + "' not found");
    }

    private void audit(ActionCode code, OperatorContext actor, String targetOperatorId, String tenantId,
                       String reason, String detail, Instant startedAt) {
        String auditId = auditor.newAuditId();
        auditor.recordWithPermission(new AdminActionAuditor.AuditRecord(
                auditId,
                code,
                actor,
                "OPERATOR",
                targetOperatorId,
                AuditReasons.normalize(reason),
                null,
                "domain-set:" + code.name() + ":" + auditId,
                Outcome.SUCCESS,
                detail,
                startedAt,
                Instant.now(),
                tenantId), Permission.DOMAIN_ACCESS_MANAGE);
    }
}
