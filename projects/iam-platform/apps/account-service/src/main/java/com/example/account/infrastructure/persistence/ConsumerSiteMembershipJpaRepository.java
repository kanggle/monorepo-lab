package com.example.account.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** TASK-BE-614 — Spring Data repository for {@code consumer_site_memberships}. */
public interface ConsumerSiteMembershipJpaRepository
        extends JpaRepository<ConsumerSiteMembershipJpaEntity, ConsumerSiteMembershipJpaEntity.MembershipId> {

    Optional<ConsumerSiteMembershipJpaEntity> findBySiteTenantIdAndAccountId(String siteTenantId, String accountId);

    /**
     * TASK-BE-615 — {@code consumer_site_roles} of one account on ONE site (no entity maps that
     * table; it has no writer yet — {@code TASK-BE-618} / {@code TASK-MONO-745}). Native so the read
     * does not need a mapping that nothing else uses.
     */
    @Query(value = "SELECT role_name FROM consumer_site_roles "
            + "WHERE site_tenant_id = :siteTenantId AND account_id = :accountId ORDER BY role_name",
            nativeQuery = true)
    List<String> findSiteRoleNames(@Param("siteTenantId") String siteTenantId,
                                   @Param("accountId") String accountId);

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

    /**
     * TASK-MONO-752 — the first HTTP writer of {@code consumer_site_roles} (consumer-site-roles.md). Native,
     * like the read above: no entity maps that table.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_by, granted_at) "
            + "VALUES (:accountId, :siteTenantId, :roleName, :grantedBy, :grantedAt)",
            nativeQuery = true)
    int insertSiteRole(@Param("accountId") String accountId,
                       @Param("siteTenantId") String siteTenantId,
                       @Param("roleName") String roleName,
                       @Param("grantedBy") String grantedBy,
                       @Param("grantedAt") Instant grantedAt);

    /**
     * TASK-BE-619 — rewrites the state of ONE existing membership row (leave / rejoin; TASK-BE-621 also
     * site lock / unlock). Native, like the insert: the entity is read-side only. Every state column is
     * written, so the row's CHECKs (V0032 · V0033) see one consistent state. Returns the number of rows
     * updated (0 = no such row).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE consumer_site_memberships SET status = :status, consented_at = :consentedAt, "
            + "left_at = :leftAt, left_by = :leftBy, left_by_actor_id = :leftByActorId, "
            + "locked_at = :lockedAt, locked_by_actor_id = :lockedByActorId "
            + "WHERE site_tenant_id = :siteTenantId AND account_id = :accountId",
            nativeQuery = true)
    int updateMembership(@Param("siteTenantId") String siteTenantId,
                         @Param("accountId") String accountId,
                         @Param("status") String status,
                         @Param("consentedAt") Instant consentedAt,
                         @Param("leftAt") Instant leftAt,
                         @Param("leftBy") String leftBy,
                         @Param("leftByActorId") String leftByActorId,
                         @Param("lockedAt") Instant lockedAt,
                         @Param("lockedByActorId") String lockedByActorId);

    /**
     * TASK-BE-619 — every site role of one account on ONE site, removed when the account leaves that
     * site (a returning member starts from the seed role only). Never another site's roles.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM consumer_site_roles WHERE site_tenant_id = :siteTenantId AND account_id = :accountId",
            nativeQuery = true)
    int deleteAllSiteRoles(@Param("siteTenantId") String siteTenantId,
                           @Param("accountId") String accountId);

    /** TASK-MONO-752 — removes one site role on ONE site; the membership row is never touched. */
    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM consumer_site_roles "
            + "WHERE site_tenant_id = :siteTenantId AND account_id = :accountId AND role_name = :roleName",
            nativeQuery = true)
    int deleteSiteRole(@Param("siteTenantId") String siteTenantId,
                       @Param("accountId") String accountId,
                       @Param("roleName") String roleName);
}
