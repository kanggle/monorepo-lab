package com.example.account.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, String> {

    Optional<AccountJpaEntity> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * TASK-BE-357: cross-tenant exact email match (SUPER_ADMIN {@code tenantId='*'} only).
     * The same email may exist under multiple tenants — {@code (tenant_id, email)} is the
     * unique index, so {@code email} alone is not unique → returns a list.
     */
    List<AccountJpaEntity> findByEmail(String email);

    Optional<AccountJpaEntity> findByTenantIdAndId(String tenantId, String id);

    boolean existsByTenantIdAndEmail(String tenantId, String email);

    /**
     * TASK-BE-372 (ADR-MONO-034 U6 step 3b): resolve the account's central
     * identity_id (V0023). Read as a native column projection so {@code identity_id}
     * stays UNMAPPED on {@link AccountJpaEntity} — Hibernate never touches the
     * column on an account update, preserving the step-3a backfilled value
     * (the merge-overwrite hazard). Returns empty when the account does not exist
     * in this tenant; returns a row whose value may be NULL when the account has
     * no identity yet (a row created before step 3d wires provisioning).
     */
    @Query(value = "SELECT identity_id FROM accounts WHERE tenant_id = :tenantId AND id = :accountId",
            nativeQuery = true)
    Optional<String> findIdentityIdByTenantIdAndId(@Param("tenantId") String tenantId,
                                                   @Param("accountId") String accountId);

    /**
     * TASK-BE-381 (ADR-MONO-036 P1/P3, M1): born-unified WRITER for the account's
     * central {@code identity_id}. Native UPDATE (mirroring the native READ
     * {@link #findIdentityIdByTenantIdAndId}) so {@code identity_id} stays UNMAPPED on
     * {@link AccountJpaEntity} — Hibernate never touches the column on an account
     * update (the merge-overwrite hazard documented above).
     *
     * <p>{@code AND identity_id IS NULL} makes it idempotent and net-zero: it only
     * sets a previously-unset value, never overwrites an existing identity (no silent
     * re-link — ADR-034 § 1.3). {@code flushAutomatically=true} flushes the pending
     * account INSERT before the UPDATE so the just-created row is visible;
     * {@code clearAutomatically=true} evicts now-stale managed entity state afterwards.
     *
     * @return rows affected (1 = assigned; 0 = already set, or no such row in tenant)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE accounts SET identity_id = :identityId "
            + "WHERE tenant_id = :tenantId AND id = :accountId AND identity_id IS NULL",
            nativeQuery = true)
    int assignIdentityIdIfAbsent(@Param("tenantId") String tenantId,
                                 @Param("accountId") String accountId,
                                 @Param("identityId") String identityId);

    @Query("SELECT a FROM AccountJpaEntity a")
    Page<AccountJpaEntity> findAllAccounts(Pageable pageable);

    /**
     * TASK-BE-475: all-tenant (platform-scope {@code "*"}) paginated listing with an
     * optional status filter — the status-aware counterpart of {@link #findAllAccounts(Pageable)}.
     * {@code null} status → all accounts (equivalent to {@code findAllAccounts}). Reached only by
     * a SUPER_ADMIN whose effective-scope gate admin-service has already enforced (mirrors the
     * tenant-scoped {@link #findByTenantIdWithStatusFilter} status hook).
     */
    @Query("SELECT a FROM AccountJpaEntity a WHERE (:status IS NULL OR a.status = :status)")
    Page<AccountJpaEntity> findAllAccountsWithStatusFilter(
            @Param("status") com.example.account.domain.status.AccountStatus status,
            Pageable pageable);

    /**
     * TASK-BE-386 (ADR-MONO-036 P4, M4): cross-tenant read of the resolved
     * {@code account_id → identity_id} bindings, used to drive the auth_db credential
     * identity backfill. Platform-level (NOT tenant-scoped) — a bulk reconciliation
     * legitimately spans all tenants, so it reads via this infrastructure repository
     * (mirroring {@link #findActiveDormantCandidates}), never via the tenant-scoped
     * {@code AccountRepository}. Native projection so {@code identity_id} stays UNMAPPED
     * on {@link AccountJpaEntity}; only already-linked accounts (post-V0024) are returned.
     */
    @Query(value = "SELECT id AS accountId, identity_id AS identityId "
            + "FROM accounts WHERE identity_id IS NOT NULL",
            nativeQuery = true)
    List<AccountIdentityBindingView> findAllIdentityBindings();

    /**
     * TASK-BE-231: Tenant-scoped paginated account listing with optional status filter.
     * Used by the internal provisioning API to list accounts within a specific tenant.
     */
    @Query("SELECT a FROM AccountJpaEntity a " +
            "WHERE a.tenantId = :tenantId " +
            "AND (:status IS NULL OR a.status = :status)")
    Page<AccountJpaEntity> findByTenantIdWithStatusFilter(
            @Param("tenantId") String tenantId,
            @Param("status") com.example.account.domain.status.AccountStatus status,
            Pageable pageable);

    /**
     * TASK-BE-614 (multi-tenancy.md § 소비자 계정 풀 § 5): the site-scoped counterpart of
     * {@link #findByTenantIdWithStatusFilter}. The input is still the <b>site</b> tenant; the
     * predicate widens from "account tenant = site" to "account tenant = site <b>or</b> (account is
     * in the pool ∧ is a member of that site)". A pool account that never joined this site — or left it —
     * is not returned. «Member» = membership ACTIVE or LOCKED (TASK-BE-621): a person that site's operator
     * locked out of the site is still on the site's list — otherwise the operator could not unlock them.
     *
     * <p>{@code :poolTenantId} is a parameter (always {@code consumer-pool}) rather than a literal so
     * the reserved value has one home ({@code TenantId.CONSUMER_POOL}).
     */
    @Query(value = "SELECT a FROM AccountJpaEntity a "
            + "WHERE (:status IS NULL OR a.status = :status) "
            + "AND (a.tenantId = :siteTenantId "
            + "     OR (a.tenantId = :poolTenantId AND EXISTS ("
            + "         SELECT 1 FROM ConsumerSiteMembershipJpaEntity m "
            + "         WHERE m.accountId = a.id AND m.siteTenantId = :siteTenantId "
            + "           AND m.status IN (com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.ACTIVE, "
            + "                            com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.LOCKED))))",
            countQuery = "SELECT COUNT(a) FROM AccountJpaEntity a "
            + "WHERE (:status IS NULL OR a.status = :status) "
            + "AND (a.tenantId = :siteTenantId "
            + "     OR (a.tenantId = :poolTenantId AND EXISTS ("
            + "         SELECT 1 FROM ConsumerSiteMembershipJpaEntity m "
            + "         WHERE m.accountId = a.id AND m.siteTenantId = :siteTenantId "
            + "           AND m.status IN (com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.ACTIVE, "
            + "                            com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.LOCKED))))")
    Page<AccountJpaEntity> findBySiteTenantIncludingPoolMembers(
            @Param("siteTenantId") String siteTenantId,
            @Param("poolTenantId") String poolTenantId,
            @Param("status") com.example.account.domain.status.AccountStatus status,
            Pageable pageable);

    /**
     * TASK-BE-614: exact-email counterpart of {@link #findBySiteTenantIncludingPoolMembers}. Returns a
     * list because a site account and a pool account with the same email are two rows — the signup
     * refusal of § 2 is what keeps that from happening, and if it ever does the caller must see both.
     */
    @Query("SELECT a FROM AccountJpaEntity a "
            + "WHERE a.email = :email "
            + "AND (a.tenantId = :siteTenantId "
            + "     OR (a.tenantId = :poolTenantId AND EXISTS ("
            + "         SELECT 1 FROM ConsumerSiteMembershipJpaEntity m "
            + "         WHERE m.accountId = a.id AND m.siteTenantId = :siteTenantId "
            + "           AND m.status IN (com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.ACTIVE, "
            + "                            com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.LOCKED)))) "
            + "ORDER BY a.tenantId ASC")
    List<AccountJpaEntity> findBySiteTenantAndEmailIncludingPoolMembers(
            @Param("siteTenantId") String siteTenantId,
            @Param("poolTenantId") String poolTenantId,
            @Param("email") String email);

    /**
     * TASK-BE-614: single-account counterpart — the account if it lives in the site tenant, or lives
     * in the pool with an ACTIVE or LOCKED (TASK-BE-621) membership of that site. A PK lookup, so at most
     * one row.
     */
    @Query("SELECT a FROM AccountJpaEntity a "
            + "WHERE a.id = :accountId "
            + "AND (a.tenantId = :siteTenantId "
            + "     OR (a.tenantId = :poolTenantId AND EXISTS ("
            + "         SELECT 1 FROM ConsumerSiteMembershipJpaEntity m "
            + "         WHERE m.accountId = a.id AND m.siteTenantId = :siteTenantId "
            + "           AND m.status IN (com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.ACTIVE, "
            + "                            com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus.LOCKED))))")
    Optional<AccountJpaEntity> findByIdInSiteIncludingPoolMembers(
            @Param("siteTenantId") String siteTenantId,
            @Param("poolTenantId") String poolTenantId,
            @Param("accountId") String accountId);

    /**
     * Returns ACTIVE accounts whose last successful login (or creation date when never logged in)
     * occurred before the given threshold. Used by AccountDormantScheduler to drive the
     * 365-day ACTIVE → DORMANT transition (retention.md §1.3, §1.4).
     */
    @Query("SELECT a FROM AccountJpaEntity a " +
            "WHERE a.status = com.example.account.domain.status.AccountStatus.ACTIVE " +
            "  AND COALESCE(a.lastLoginSucceededAt, a.createdAt) < :threshold")
    List<AccountJpaEntity> findActiveDormantCandidates(@Param("threshold") Instant threshold);

    /**
     * Find DELETED accounts whose grace period (30 days, see retention.md §2)
     * has expired and whose profile has not yet been anonymized.
     *
     * @param threshold cut-off instant; rows with {@code deleted_at < threshold} are eligible.
     *                  Caller passes {@code Instant.now().minus(30, DAYS)}.
     */
    @Query("""
            SELECT a FROM AccountJpaEntity a
            LEFT JOIN ProfileJpaEntity p ON p.accountId = a.id
            WHERE a.status = com.example.account.domain.status.AccountStatus.DELETED
              AND a.deletedAt < :threshold
              AND (p.maskedAt IS NULL OR p.accountId IS NULL)
            """)
    List<AccountJpaEntity> findAnonymizationCandidates(@Param("threshold") Instant threshold);
}
