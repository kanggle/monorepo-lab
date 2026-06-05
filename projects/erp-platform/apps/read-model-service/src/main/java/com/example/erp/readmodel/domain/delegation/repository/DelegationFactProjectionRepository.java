package com.example.erp.readmodel.domain.delegation.repository;

import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for {@link DelegationFactProjection} (Hexagonal — the domain
 * owns the interface; the JPA adapter implements it; TASK-ERP-BE-015). Read-only
 * projection store (E5); the only mutation is the consumer-side latest-fact
 * upsert.
 */
public interface DelegationFactProjectionRepository {

    Optional<DelegationFactProjection> findById(String grantId);

    /**
     * Paginated delegation-fact page matching the given filter
     * ({@link DelegationFactFilter}). The {@code org_scope} read filter is applied
     * via the filter's pre-resolved in-scope delegator id set (the use case
     * resolves the delegator → department mapping; the repository only applies the
     * id set):
     * <ul>
     *   <li>{@code scopeUnbounded == true} → no org_scope narrowing (net-zero).</li>
     *   <li>otherwise only facts whose {@code delegatorId} is in
     *       {@code scopedDelegatorIds} are returned.</li>
     * </ul>
     */
    List<DelegationFactProjection> findPage(DelegationFactFilter filter, int page, int size);

    long count(DelegationFactFilter filter);

    void save(DelegationFactProjection projection);
}
