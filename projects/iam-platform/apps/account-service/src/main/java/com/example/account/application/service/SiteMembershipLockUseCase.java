package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-621 (owner decision 2026-10-04 «사이트 운영자(예: 스토어 운영자)가 회원을 잠글 때, 그 잠금은 자기
 * 사이트에만 걸린다. 계정 전체 잠금은 플랫폼 관리자만.»; multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 잠금 vs
 * 계정 잠금») — a site operator's lock / unlock of a consumer-POOL member changes only that site's membership.
 *
 * <p>The account, its status history, every OTHER site's membership and the site's own
 * {@code consumer_site_roles} are untouched; no outbox event is published (the same stance as
 * {@link LeaveConsumerSiteUseCase}, TASK-BE-619 D-4): a LOCKED membership already admits no token for the site
 * at the next authorize or refresh (TASK-BE-615 — only ACTIVE issues), and the person's IAM session and their
 * other sites must keep working — that is the point of the decision. The audit record of who locked is the
 * membership row ({@code locked_at} · {@code locked_by_actor_id}) and admin-service's {@code admin_actions} row.
 *
 * <p>Reached only from {@link AccountStatusUseCase#changeStatusAsTenantOperator} — a caller that NAMED a site
 * tenant and whose target, found through that site ({@link SiteAccountLookup}), is a pool account. Locking the
 * whole account is the unnamed-tenant path (platform admin {@code "*"}, automatic lock).
 *
 * <ul>
 *   <li>{@code LOCKED} target: ACTIVE → LOCKED; LOCKED → unchanged (idempotent, 200).</li>
 *   <li>{@code ACTIVE} target: LOCKED → ACTIVE; ACTIVE → unchanged (idempotent, 200). A site operator cannot
 *       lift a whole-account lock this way — only the membership is looked at.</li>
 *   <li>The account is DELETED, or any other target → {@link StateTransitionException} (409).</li>
 *   <li>No membership row, or LEFT → {@link AccountNotFoundException} (404) — not a member of the site.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteMembershipLockUseCase {

    private final ConsumerSiteMembershipRepository membershipRepository;

    @Transactional
    public StatusChangeResult execute(TenantId site, Account account, AccountStatus target,
                                      StatusChangeReason reason, String operatorId) {
        Objects.requireNonNull(site, "site is required");
        Objects.requireNonNull(account, "account is required");
        if (account.getStatus() == AccountStatus.DELETED
                || (target != AccountStatus.LOCKED && target != AccountStatus.ACTIVE)) {
            throw new StateTransitionException(account.getStatus(), target, reason);
        }
        String accountId = account.getId();
        ConsumerSiteMembership current = membershipRepository.find(site, accountId)
                .filter(m -> m.getStatus() != ConsumerSiteMembershipStatus.LEFT)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        Instant now = Instant.now();
        ConsumerSiteMembership next = target == AccountStatus.LOCKED
                ? current.lockBySiteOperator(operatorId, now)
                : current.unlockBySiteOperator();
        if (next != current) {
            membershipRepository.update(next);
            log.info("consumer-site {}: account {} on site {} by operator {} ({} -> {})",
                    target == AccountStatus.LOCKED ? "lock" : "unlock",
                    accountId, site.value(), operatorId, current.getStatus(), next.getStatus());
        }
        return StatusChangeResult.siteMembership(accountId, current.getStatus().name(), next.getStatus().name(),
                now, site.value());
    }
}
