package com.example.admin.application;

import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * TASK-MONO-784 S5 (ADR-MONO-082 b · D2 · D4 · implementer decision D-12) — the ONE place that adds a company
 * membership to an operator row that already exists. Both «두 409» paths call it — the invitation acceptance
 * (auth-to-admin.md § accept step 6 ②) and self-service onboarding (onboarding-api.md § 다회사 운영자와 온보딩 row
 * 5) — and TASK-MONO-773's «테넌트 생성» is meant to call the same place (784 AC-0 § 7 «773 을 막지 않는 조건» ①).
 *
 * <p><b>Can this row receive the membership?</b> ({@link #requireCanReceive}) — first failure answers, all
 * {@code 409 OPERATOR_ALREADY_PROVISIONED} (the code stays; its meaning narrowed to «this facet cannot take this
 * membership», so the IdP and console mappings are unchanged):
 * <ol>
 *   <li>a platform ({@code '*'}) row — home {@code '*'} or any {@code '*'} role row (ADR-082 D2: a platform
 *       administrator takes no company membership; either marker is enough — the stricter reading);</li>
 *   <li>a non-{@code ACTIVE} row (a suspended person does not revive a facet through a new membership);</li>
 *   <li>already a member of the target tenant (an {@code operator_tenant_assignment} row exists).</li>
 * </ol>
 * The home is not read for membership (R1) — it is only the {@code '*'} platform marker in step 1, as everywhere
 * else in 784 (S4 {@code ManageOperatorAssignmentUseCase}).
 *
 * <p><b>What the addition writes</b> ({@link #write}) — 🔴 ONLY rows of the target tenant: one direct role row per
 * role ({@code tenant_id} = target, never the home, never {@code '*'}) and the whole-tenant assignment. The rows
 * of every other company, the operator row itself (home · {@code oidc_subject} · {@code email} ·
 * {@code display_name} · {@code status}) and the identity link are not touched (ADR-082 D4 · 784 AC-3 «기존 회사
 * 역할 행 바이트 불변»). It runs inside the CALLER's transaction (the acceptance writer, the onboarding
 * provisioner) so the membership and whatever accompanies it commit or roll back together.
 *
 * <p>After the caller's transaction commits it calls {@link #invalidatePermissionCache} — the row gained
 * permissions (D-5); best-effort, like every other invalidation point.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorMembershipAddition {

    static final String ACTIVE = "ACTIVE";

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final CachingPermissionEvaluator cachingPermissionEvaluator;

    /**
     * @throws OperatorAlreadyProvisionedException the row is {@code '*'}, not {@code ACTIVE}, or already a member
     *                                             of {@code tenantId}
     * @throws IllegalArgumentException            {@code tenantId} is {@code '*'} (ADR-082 D2 · D-14 — a company
     *                                             row never receives a {@code '*'} role row; 400)
     */
    public void requireCanReceive(AdminOperatorPort.OperatorView facet, String tenantId) {
        requireCompanyTenant(tenantId);
        if (isPlatformRow(facet)) {
            throw new OperatorAlreadyProvisionedException(
                    "This account's operator facet is a platform operator and takes no company membership");
        }
        if (!ACTIVE.equals(facet.status())) {
            throw new OperatorAlreadyProvisionedException(
                    "This account's operator facet is not ACTIVE and cannot take a new membership");
        }
        if (assignmentPort.assignmentExists(facet.internalId(), tenantId)) {
            throw new OperatorAlreadyProvisionedException(
                    "This account's operator facet is already a member of the tenant");
        }
    }

    /**
     * The writes of one membership addition — target-tenant rows only (see class javadoc). Call inside the
     * caller's transaction, after {@link #requireCanReceive}.
     *
     * @param grantedBy {@code granted_by} of the role rows and the assignment ({@code null} = system origination —
     *                  self-service onboarding)
     */
    public void write(long operatorInternalId, String tenantId, Collection<AdminOperatorPort.RoleView> roles,
                      Long grantedBy, Instant now) {
        requireCompanyTenant(tenantId);
        List<AdminOperatorPort.NewRoleBinding> bindings = roles.stream()
                .map(role -> new AdminOperatorPort.NewRoleBinding(operatorInternalId, role.id(), now, grantedBy,
                        tenantId))
                .toList();
        operatorPort.saveOperatorRoles(bindings);
        assignmentPort.createAssignment(operatorInternalId, tenantId, grantedBy);
    }

    /** D-5 — after the membership committed. Best-effort: the cache TTL bounds a failed eviction. */
    public void invalidatePermissionCache(String operatorUuid) {
        if (cachingPermissionEvaluator == null) return;
        try {
            cachingPermissionEvaluator.invalidate(operatorUuid);
        } catch (RuntimeException ex) {
            log.warn("Permission cache invalidate failed for operatorId={} cause={}",
                    operatorUuid, ex.getClass().getSimpleName());
        }
    }

    private boolean isPlatformRow(AdminOperatorPort.OperatorView facet) {
        if (AdminOperator.PLATFORM_TENANT_ID.equals(facet.tenantId())) {
            return true;
        }
        return operatorPort.findRoleRowsOfOperator(facet.internalId()).stream()
                .anyMatch(row -> AdminOperator.PLATFORM_TENANT_ID.equals(row.tenantId()));
    }

    private static void requireCompanyTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || AdminOperator.PLATFORM_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException("A company membership needs a company tenant, not '*'");
        }
    }
}
