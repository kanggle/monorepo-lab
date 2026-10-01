package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/** TASK-BE-614 — Spring Data repository for {@code consumer_site_memberships}. */
public interface ConsumerSiteMembershipJpaRepository
        extends JpaRepository<ConsumerSiteMembershipJpaEntity, ConsumerSiteMembershipJpaEntity.MembershipId> {

    Optional<ConsumerSiteMembershipJpaEntity> findBySiteTenantIdAndAccountId(String siteTenantId, String accountId);

    /**
     * Native INSERT with {@code flushAutomatically = true}: the pending {@code accounts} INSERT of
     * the same signup transaction is flushed <b>before</b> this statement runs, so
     * {@code fk_csm_account} always sees its parent row (the same technique
     * {@link AccountJpaRepository#assignIdentityIdIfAbsent} uses). A duplicate
     * {@code (account_id, site_tenant_id)} raises a {@code DataIntegrityViolationException}.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at) "
            + "VALUES (:accountId, :siteTenantId, :status, :consentedAt)",
            nativeQuery = true)
    int insertMembership(@Param("accountId") String accountId,
                         @Param("siteTenantId") String siteTenantId,
                         @Param("status") String status,
                         @Param("consentedAt") Instant consentedAt);
}
