package com.example.erp.approval.domain.delegation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Resolves the acting principal of an approve/reject against the current stage's
 * approver A (architecture.md § v2.1 amendment — transition-time resolution):
 *
 * <ol>
 *   <li>{@code actorId == stageApproverId} → {@link DelegationResolution#direct()}.</li>
 *   <li>else an ACTIVE grant {@code stageApproverId → actorId} containing
 *       {@code now} → {@link DelegationResolution#delegated(String) delegated(A)}.</li>
 *   <li>neither → {@link DelegationResolution#notAuthorized()} (fail-closed; the
 *       use case maps this to {@code APPROVAL_NOT_AUTHORIZED_APPROVER}).</li>
 * </ol>
 *
 * <p>A DB lookup failure surfaces as the repository's exception (the transition Tx
 * aborts — fail-closed, no bypass). This collaborator is the single place that
 * decides "is this principal allowed to act for the stage approver"; the
 * Separation-of-Duties check (delegate ≠ submitter) is applied by the use case
 * (it needs the request's submitter, which is not a delegation concern).
 */
@Component
@RequiredArgsConstructor
public class DelegationResolver {

    private final DelegationGrantRepository delegationGrantRepository;

    public DelegationResolution resolve(String stageApproverId, String actorId,
                                        String tenantId, Instant now) {
        if (stageApproverId.equals(actorId)) {
            return DelegationResolution.direct();
        }
        return delegationGrantRepository
                .findActiveGrant(stageApproverId, actorId, tenantId, now)
                .filter(g -> g.isActiveAt(now))
                .map(g -> DelegationResolution.delegated(stageApproverId))
                .orElseGet(DelegationResolution::notAuthorized);
    }
}
