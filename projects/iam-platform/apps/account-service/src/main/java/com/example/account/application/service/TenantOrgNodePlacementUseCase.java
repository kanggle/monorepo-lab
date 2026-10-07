package com.example.account.application.service;

import com.example.account.application.exception.OrgNodeNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantOrgNodeConflictException;
import com.example.account.application.result.TenantPlacementResult;
import com.example.account.domain.orgnode.EntitlementCeiling;
import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.repository.OrgNodeRepository;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantDomainSubscription;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — the write that puts a tenant under an org-node,
 * moves it between nodes, or takes it out ({@code null} = ungrouped). The first caller of
 * {@link Tenant#assignOrgNode}.
 *
 * <p><b>This class does not authorize.</b> Who may move a tenant ("both sides must be
 * administered by the actor") is decided by admin-service, which owns the IAM plane. What
 * lives here is what only the data owner can do correctly:
 * <ul>
 *   <li><b>existence</b> — the tenant, and the target node (the authority on a node deleted
 *       a moment ago);</li>
 *   <li><b>compare-then-write</b> — the write lands only if the tenant is still where
 *       admin-service authorized it to be ({@code expectedOrgNodeId}), under a row lock;</li>
 *   <li><b>the effect</b> — {@code ACTIVE subscriptions ∩ ceiling} before and after (rider P2).</li>
 * </ul>
 *
 * <p>Contract: {@code specs/contracts/http/internal/admin-to-account.md} § Tenant placement.
 */
@Service
@RequiredArgsConstructor
public class TenantOrgNodePlacementUseCase {

    private final TenantRepository tenantRepository;
    private final OrgNodeRepository orgNodeRepository;
    private final TenantDomainSubscriptionRepository subscriptionRepository;
    private final OrgNodeQueryUseCase orgNodeQueryUseCase;

    /**
     * @return the tenant's current org-node id, or {@code null} when it is ungrouped
     * @throws TenantNotFoundException the tenant does not exist
     */
    @Transactional(readOnly = true)
    public String currentOrgNodeId(String tenantId) {
        Tenant tenant = tenantRepository.findById(new TenantId(tenantId))
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return idOf(tenant.getOrgNodeId());
    }

    /**
     * The effect of moving {@code tenantId} to {@code targetOrgNodeId} — nothing is written.
     *
     * @throws TenantNotFoundException  the tenant does not exist
     * @throws OrgNodeNotFoundException the target node does not exist
     */
    @Transactional(readOnly = true)
    public TenantPlacementResult preview(String tenantId, String targetOrgNodeId) {
        Tenant tenant = tenantRepository.findById(new TenantId(tenantId))
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        OrgNodeId target = requireNodeOrNull(targetOrgNodeId);
        return effect(tenantId, tenant.getOrgNodeId(), target, false);
    }

    /**
     * Moves the tenant. Order matters and mirrors the contract:
     * <ol>
     *   <li>tenant missing → 404;</li>
     *   <li>current ≠ {@code expectedOrgNodeId} → 409, nothing written;</li>
     *   <li>target node missing → 404, nothing written;</li>
     *   <li>target = current → idempotent no-op ({@code changed=false});</li>
     *   <li>otherwise {@link Tenant#assignOrgNode} and save.</li>
     * </ol>
     *
     * <p>A node deleted after step 3 but before commit is caught by the
     * {@code fk_tenants_org_node} foreign key at commit; the controller maps that to 404 too.
     *
     * @param targetOrgNodeId   destination, {@code null} = ungrouped (detach)
     * @param expectedOrgNodeId where admin-service authorized the tenant to be, {@code null} = ungrouped
     */
    @Transactional
    public TenantPlacementResult place(String tenantId, String targetOrgNodeId, String expectedOrgNodeId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(new TenantId(tenantId))
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        String current = idOf(tenant.getOrgNodeId());
        String expected = blankToNull(expectedOrgNodeId);
        if (!Objects.equals(current, expected)) {
            throw new TenantOrgNodeConflictException(tenantId, expected, current);
        }

        OrgNodeId target = requireNodeOrNull(targetOrgNodeId);
        boolean changed = !Objects.equals(current, idOf(target));

        // The effect is computed against the ceilings as they are now, before the write.
        TenantPlacementResult result = effect(tenantId, tenant.getOrgNodeId(), target, changed);

        if (changed) {
            tenant.assignOrgNode(target, Instant.now());
            tenantRepository.save(tenant);
        }
        return result;
    }

    // ---- internals ----------------------------------------------------------------

    private TenantPlacementResult effect(String tenantId, OrgNodeId from, OrgNodeId to, boolean changed) {
        List<String> active = subscriptionRepository.findActiveByTenantId(tenantId).stream()
                .map(TenantDomainSubscription::getDomainKey)
                .toList();
        EntitlementCeiling before = ceilingOf(from);
        EntitlementCeiling after = ceilingOf(to);

        List<String> domainsBefore = active.stream().filter(before::permits).toList();
        List<String> domainsAfter = active.stream().filter(after::permits).toList();
        List<String> lost = domainsBefore.stream().filter(d -> !domainsAfter.contains(d)).toList();
        List<String> gained = domainsAfter.stream().filter(d -> !domainsBefore.contains(d)).toList();

        return new TenantPlacementResult(tenantId, idOf(from), idOf(to),
                domainsBefore, domainsAfter, lost, gained, changed);
    }

    /** An ungrouped tenant is UNBOUNDED — the intersection identity (D7), never the empty set. */
    private EntitlementCeiling ceilingOf(OrgNodeId nodeId) {
        return nodeId == null ? EntitlementCeiling.unbounded() : orgNodeQueryUseCase.effectiveCeiling(nodeId);
    }

    private OrgNodeId requireNodeOrNull(String orgNodeId) {
        String id = blankToNull(orgNodeId);
        if (id == null) {
            return null;
        }
        OrgNodeId nodeId = new OrgNodeId(id);
        if (orgNodeRepository.findById(nodeId).isEmpty()) {
            throw new OrgNodeNotFoundException(id);
        }
        return nodeId;
    }

    private static String idOf(OrgNodeId id) {
        return id == null ? null : id.value();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
