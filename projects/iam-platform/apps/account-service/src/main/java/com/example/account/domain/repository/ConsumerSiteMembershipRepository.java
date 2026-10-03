package com.example.account.domain.repository;

import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-614 — port for {@code consumer_site_memberships} (ADR-MONO-078 A).
 *
 * <p>Follows the repository rule of multi-tenancy.md § 격리 회귀 방지: every lookup takes the
 * <b>site</b> tenant as its first argument.
 */
public interface ConsumerSiteMembershipRepository {

    /**
     * Inserts a new membership. The pool account row must already be written in the same
     * transaction (FK {@code account_id → accounts.id}); the implementation flushes pending
     * writes first so that ordering holds regardless of Hibernate's insert ordering.
     */
    void insert(ConsumerSiteMembership membership);

    /** The membership of {@code accountId} on {@code siteTenantId}, if any. */
    Optional<ConsumerSiteMembership> find(TenantId siteTenantId, String accountId);

    /**
     * TASK-BE-619 — writes the new state of an EXISTING membership row (leave / rejoin): status,
     * {@code consented_at} and the leave record. Never inserts.
     *
     * @return {@code true} iff the row existed and was updated
     */
    boolean update(ConsumerSiteMembership membership);

    /**
     * TASK-BE-619 — removes every {@code consumer_site_roles} row of the account on {@code siteTenantId}
     * only (leaving a site drops its site roles). Never another site's roles, never the membership.
     *
     * @return the number of roles removed
     */
    int removeAllSiteRoles(TenantId siteTenantId, String accountId);

    /**
     * TASK-BE-615 — the account's site roles outside the seed ({@code consumer_site_roles}) on
     * {@code siteTenantId} only, ascending by role name. Never another site's roles: the site is
     * the first argument and the only scope. Empty when there are none.
     */
    List<String> findSiteRoles(TenantId siteTenantId, String accountId);

    /**
     * TASK-MONO-752 — inserts one {@code consumer_site_roles} row. The caller has already checked that the
     * role is absent and the ACTIVE membership exists (FK); a concurrent duplicate raises a
     * {@code DataIntegrityViolationException}.
     */
    void addSiteRole(TenantId siteTenantId, String accountId, String roleName, String grantedBy, Instant grantedAt);

    /**
     * TASK-MONO-752 — deletes one {@code consumer_site_roles} row on ONE site. Never touches the
     * membership or the account.
     *
     * @return {@code true} iff a row was deleted
     */
    boolean removeSiteRole(TenantId siteTenantId, String accountId, String roleName);
}
