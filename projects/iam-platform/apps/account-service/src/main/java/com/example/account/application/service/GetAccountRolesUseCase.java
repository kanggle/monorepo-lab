package com.example.account.application.service;

import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-368 (ADR-MONO-033 S2 / ADR-032 step 2.1): Read-only roles lookup for
 * the internal roles read endpoint.
 *
 * <p>Returns the role names assigned to the given account within the tenant.
 * A foreign or missing account yields an empty list (enumeration-safe — no 404).
 * The caller (auth-service) fail-softs on an empty result.
 *
 * <p><b>TASK-BE-618 widening</b> (account-internal-provisioning.md § roles GET; multi-tenancy.md § 소비자 계정
 * 풀 § 3 · § 5): when the pool flag is on, the tenant is a <b>consumer site</b>, the site has no stored
 * {@code account_roles} for the account, and the account is a {@code consumer-pool} account holding an
 * ACTIVE membership of that site, the answer is its {@code consumer_site_roles} on that site. Why: a session
 * that logged in BEFORE the legacy move keeps a site principal, so its refresh asks this endpoint with the
 * site tenant — and the move took the site's {@code account_roles} into {@code consumer_site_roles}. The input
 * is still the site and only that site's members answer (the § 5 predicate) — an extension of § 격리 회귀
 * 방지, not an exception. Every other case is byte-for-byte the old read.
 *
 * <p>Net-zero: no audit row, no outbox event, no mutation.
 */
@Service
@RequiredArgsConstructor
public class GetAccountRolesUseCase {

    private final AccountRoleRepository accountRoleRepository;
    private final ConsumerPoolFlag consumerPoolFlag;
    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;

    /**
     * Returns all role names assigned to {@code accountId} within {@code tenantId}.
     * Returns an empty list when the account has no roles or does not exist in this tenant.
     *
     * @param tenantId  the tenant scope (slug string)
     * @param accountId the account identifier
     * @return list of role name strings (possibly empty, never null)
     */
    @Transactional(readOnly = true)
    public List<String> execute(String tenantId, String accountId) {
        TenantId tid = new TenantId(tenantId);
        List<String> stored = accountRoleRepository.findByTenantIdAndAccountId(tid, accountId)
                .stream()
                .map(AccountRole::getRoleName)
                .toList();
        if (!stored.isEmpty() || !consumerPoolFlag.isEnabled() || tid.isConsumerPool()) {
            return stored;
        }
        boolean consumerSite = tenantRepository.findById(tid).map(Tenant::isConsumerSite).orElse(false);
        if (!consumerSite || accountRepository.findById(TenantId.CONSUMER_POOL, accountId).isEmpty()) {
            return stored;
        }
        return membershipRepository.find(tid, accountId)
                .filter(ConsumerSiteMembership::isActive)
                .map(m -> membershipRepository.findSiteRoles(tid, accountId))
                .orElse(stored);
    }
}
