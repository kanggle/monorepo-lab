package com.example.account.domain.repository;

import com.example.account.domain.consumerpool.LegacyMoveCandidate;
import com.example.account.domain.consumerpool.LegacySiteAccount;
import com.example.account.domain.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * TASK-BE-618 (multi-tenancy.md § 소비자 계정 풀 § 3; account-maintenance-internal.md) — the account_db
 * writes of moving one single-site account into the consumer pool under the same id.
 *
 * <p>Follows § 격리 회귀 방지: every lookup takes the SITE tenant(s) as its first argument — the site the
 * account lives in now is the input, the pool is only ever a write target.
 *
 * <p>Every method runs inside the caller's per-account transaction; nothing here commits on its own.
 */
public interface ConsumerPoolLegacyMoveRepository {

    /**
     * Candidates: accounts whose tenant is one of {@code consumerSites} and whose status is not DELETED,
     * with id {@code > afterAccountId} (all when {@code null}), ascending by id, at most {@code limit}.
     */
    List<LegacyMoveCandidate> findCandidates(List<TenantId> consumerSites, String afterAccountId, int limit);

    /**
     * The account row, locked ({@code SELECT … FOR UPDATE}), when it still lives in {@code siteTenantId}
     * and is not DELETED; empty otherwise (a concurrent run moved it, it was deleted, …).
     */
    Optional<LegacySiteAccount> lockSiteAccount(TenantId siteTenantId, String accountId);

    /** The account's stored role names on {@code siteTenantId} ({@code account_roles}), ascending. */
    List<String> findSiteRoleNames(TenantId siteTenantId, String accountId);

    /**
     * Whether moving the account's identity row into the pool would collide: a {@code consumer-pool}
     * identity with the same {@code primary_email} as {@code identityId}'s row exists, or another account
     * also points at {@code identityId}.
     */
    boolean identityWouldConflict(TenantId siteTenantId, String identityId, String accountId);

    /**
     * Inserts the membership {@code (accountId, siteTenantId, ACTIVE, consented_at = accounts.created_at)}
     * — signing up on the site was consenting to it (§ 2). {@code consented_at} is copied from the account
     * row in SQL so it is byte-for-byte the creation time (no JVM time-zone round trip).
     */
    void insertActiveMembershipConsentedAtCreation(TenantId siteTenantId, String accountId);

    /**
     * Copies every {@code account_roles(siteTenantId, accountId)} row into
     * {@code consumer_site_roles(accountId, siteTenantId, role_name, granted_by, granted_at)} — the
     * membership row must already exist (FK).
     *
     * @return rows copied
     */
    int copySiteRolesToConsumerSiteRoles(TenantId siteTenantId, String accountId);

    /**
     * Deletes {@code account_roles(siteTenantId, accountId)} — must run BEFORE the account's tenant changes
     * (composite FK {@code (tenant_id, account_id) → accounts(tenant_id, id)}).
     *
     * @return rows deleted
     */
    int deleteSiteRoles(TenantId siteTenantId, String accountId);

    /**
     * Sets {@code tenant_id = 'consumer-pool'} on the {@code accounts} row (guarded on
     * {@code siteTenantId}, {@code version + 1}), on its {@code profiles} row, and — when
     * {@code identityId} is non-null — on that {@code identities} row ({@code version + 1}).
     * {@code account_status_history} is NOT touched: it is append-only (DB trigger).
     *
     * @return {@code accounts} rows moved (1, or 0 when the row no longer lives in {@code siteTenantId})
     */
    int moveAccountRowsToPool(TenantId siteTenantId, String accountId, String identityId);
}
