package com.example.account.application.service;

import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-619 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 탈퇴 vs 계정 삭제») — a pool
 * account stops using ONE consumer site: its membership of that site becomes {@code LEFT}.
 *
 * <p>Owner decisions (2026-10-03):
 * <ul>
 *   <li>«사이트 운영자 삭제 권한 = 자기 사이트 멤버십만» — this is the ONLY thing a site operator can do to
 *       a pool account's existence on their site. The account, its PII and every OTHER site's membership
 *       are untouched; deleting the pool account is for the person or a platform admin.</li>
 *   <li>«탈퇴 후 복귀 = 다시 동의하면 복귀» — who left is recorded ({@link ConsumerSiteLeftBy}): a SELF leave
 *       is reopened by consenting again ({@link ConsentToConsumerSiteUseCase}), an OPERATOR one is not.</li>
 * </ul>
 *
 * <p>What leaving writes, in one transaction: the membership row (status · {@code left_at} · {@code left_by}
 * · {@code left_by_actor_id}) and the removal of that site's {@code consumer_site_roles} rows — a member
 * who comes back starts from the site's seed role only, so a role an operator once granted is not
 * silently restored by a later consent. No account row, no status history row (the account's lifecycle
 * did not change), no outbox event: a LEFT membership already admits no token for the site at the next
 * authorize or refresh (TASK-BE-615), and what the site does with its own copy of the person's data is the
 * site's business — out of this ticket.
 *
 * <p><b>Idempotent.</b> Leaving an already-LEFT membership writes nothing and answers the current row,
 * except that the site's operator removing a person who had left themself re-records it as OPERATOR — the
 * removal must stick. A person leaving never downgrades an operator removal.
 *
 * <p><b>Refusals</b> ({@link SiteMembershipRequiredException} → 409 {@code SITE_MEMBERSHIP_REQUIRED}): the
 * tenant is not a consumer site, the account is not a pool account (a site account has no separate
 * membership — leaving IS deleting it), or there is no membership row for the site.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveConsumerSiteUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final ConsumerSiteMembershipRepository membershipRepository;

    @Transactional
    public LeaveConsumerSiteResult execute(String siteTenantId, String accountId, ConsumerSiteLeftBy by,
                                           String actorId) {
        Objects.requireNonNull(by, "by is required");
        TenantId site = new TenantId(siteTenantId);
        boolean consumerSite = tenantRepository.findById(site).map(Tenant::isConsumerSite).orElse(false);
        if (!consumerSite) {
            throw new SiteMembershipRequiredException("not a consumer site: " + siteTenantId);
        }
        Account account = accountRepository.findById(TenantId.CONSUMER_POOL, accountId)
                .orElseThrow(() -> new SiteMembershipRequiredException(
                        "not a consumer-pool account — a site account has no separate site membership"));
        ConsumerSiteMembership current = membershipRepository.find(site, accountId)
                .orElseThrow(() -> new SiteMembershipRequiredException(
                        "no membership of site " + siteTenantId + " to leave"));

        ConsumerSiteMembership next = current.leave(by, actorId, Instant.now());
        boolean changed = next != current;
        if (changed) {
            membershipRepository.update(next);
            int rolesRemoved = membershipRepository.removeAllSiteRoles(site, accountId);
            log.info("consumer-site leave: account {} left site {} (by {}, {} site role(s) removed)",
                    accountId, siteTenantId, by, rolesRemoved);
        }
        return new LeaveConsumerSiteResult(
                accountId,
                siteTenantId,
                next.getStatus().name(),
                next.getLeftBy() == null ? null : next.getLeftBy().name(),
                next.getLeftAt(),
                changed,
                account.getStatus().name());
    }
}
