package com.example.fanplatform.membership.domain.membership;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@link Membership} aggregate. Every query is
 * tenant-scoped (multi-tenant.md M2). The infrastructure adapter
 * ({@code infrastructure.jpa}) implements this over Spring Data JPA.
 */
public interface MembershipRepository {

    Membership save(Membership membership);

    /** Tenant + account scoped point lookup by id (cross-tenant/cross-account → empty). */
    Optional<Membership> findByIdScoped(String id, String accountId, String tenantId);

    /** The caller's memberships, newest window first. */
    List<Membership> findByAccount(String accountId, String tenantId);

    /**
     * ACTIVE memberships for the (account, tenant) pair — the access-check
     * candidate set (window + tier are evaluated in-memory by the use case).
     */
    List<Membership> findActiveByAccount(String accountId, String tenantId);
}
