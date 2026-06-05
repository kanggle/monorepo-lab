package com.example.erp.readmodel.domain.delegation.repository;

import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;

import java.time.Instant;
import java.util.List;

/**
 * Query filter for the delegation-fact list (TASK-ERP-BE-015). Carries the
 * optional explicit query filters (delegatorId / delegateId / status / activeAt)
 * plus the pre-resolved {@code org_scope} read-filter id set.
 *
 * <p><b>activeAt semantics</b> (resolved by the repository): when present, the
 * row must be {@code status = ACTIVE} AND {@code validFrom <= activeAt} AND
 * ({@code validTo} is null OR {@code activeAt <= validTo}).
 *
 * <p><b>org_scope semantics</b> (resolved by the query use case, applied by the
 * repository — mirrors the approval-fact read filter; the scope is the
 * <b>delegator</b>'s department subtree):
 * <ul>
 *   <li>{@code scopeUnbounded == true} → no narrowing (net-zero — the
 *       {@code org_scope=["*"]}/absent caller sees all delegation facts).</li>
 *   <li>{@code scopeUnbounded == false} → only facts whose {@code delegatorId} is
 *       in {@code scopedDelegatorIds} (delegators whose department is in the
 *       operator's subtree). The set may be empty (zero-scope → an empty result,
 *       fail-closed).</li>
 * </ul>
 */
public record DelegationFactFilter(
        String delegatorId,
        String delegateId,
        DelegationFactStatus status,
        Instant activeAt,
        boolean scopeUnbounded,
        List<String> scopedDelegatorIds
) {

    public DelegationFactFilter {
        scopedDelegatorIds = scopedDelegatorIds == null
                ? List.of() : List.copyOf(scopedDelegatorIds);
    }

    /** A net-zero (platform / unbounded) filter carrying only the explicit query filters. */
    public static DelegationFactFilter unbounded(String delegatorId, String delegateId,
                                                 DelegationFactStatus status, Instant activeAt) {
        return new DelegationFactFilter(delegatorId, delegateId, status, activeAt,
                true, List.of());
    }
}
