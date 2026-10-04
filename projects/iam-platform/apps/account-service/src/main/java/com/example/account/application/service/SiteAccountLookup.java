package com.example.account.application.service;

import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

/**
 * TASK-BE-616 (multi-tenancy.md § 소비자 계정 풀 § 5) — the ONE rule for finding a single account by a
 * request-supplied tenant (a gateway {@code X-Tenant-Id}, an operator's active tenant, a
 * {@code /internal/tenants/{tenantId}/…} path).
 *
 * <p>§ 5: a surface that finds accounts by a SITE tenant must include the pool accounts that are ACTIVE
 * members of that site — the predicate widens from «account tenant = input» to «account tenant = input
 * <b>or</b> (pool ∧ ACTIVE member of input)». TASK-BE-614 applied it to the list / search / single
 * {@code /internal/tenants/{t}/accounts} reads; this applies the same predicate to every other
 * single-account lookup that is keyed on a site tenant (census: TASK-BE-616 구현 기록).
 *
 * <p>TASK-BE-621: «member» = membership ACTIVE <b>or LOCKED</b> — a person that site's operator locked out
 * of the site is still found through it (so the operator can see and unlock them, and erase them as
 * TASK-BE-619 allows). LEFT stays invisible.
 *
 * <ul>
 *   <li>The input is still the first argument and still the scope: a pool account that is NOT an ACTIVE
 *       member of the input site stays invisible there (404) — site B cannot see a pool account that only
 *       joined site A. This is an extension of § 격리 회귀 방지, not an exception: no tenant-less lookup.</li>
 *   <li>Input {@code consumer-pool} itself → exact match only (an internal caller that already knows
 *       the account is in the pool, e.g. auth-service's status check for a pool principal).</li>
 *   <li>Behind {@link ConsumerPoolFlag}, like the 614 lookups: off → {@code findById(tenant, id)}
 *       byte-for-byte.</li>
 * </ul>
 *
 * <p>Deliberately NOT used by the {@code account_roles} writers (their composite FK
 * {@code (tenant_id, account_id) → accounts(tenant_id, id)} cannot hold a pool account under a site
 * tenant; site roles of pool accounts live in {@code consumer_site_roles}).
 */
public final class SiteAccountLookup {

    private SiteAccountLookup() {
    }

    public static Optional<Account> find(AccountRepository accountRepository, ConsumerPoolFlag flag,
                                         TenantId tenantId, String accountId) {
        if (flag.isEnabled() && !tenantId.isConsumerPool()) {
            return accountRepository.findByIdInSiteIncludingPoolMembers(tenantId, accountId);
        }
        return accountRepository.findById(tenantId, accountId);
    }
}
