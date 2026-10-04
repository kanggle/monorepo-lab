package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.status.StatusTransition;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * TASK-BE-231: Tenant-scoped account status change for the internal provisioning API.
 *
 * <p>Records audit with {@code OPERATOR_PROVISIONING_STATUS_CHANGE} and publishes
 * the outbox {@code account.status.changed} event with the correct {@code tenant_id}.
 *
 * <p><b>TASK-BE-622</b> (owner decision 2026-10-04, {@code TASK-BE-621} § 소유자 결정 2 «별도 티켓으로 적용»;
 * account-internal-provisioning.md § Consumer-pool member) — when the path names a consumer SITE and the target,
 * found through it ({@link SiteAccountLookup}), is a consumer-POOL account, the site backend's call changes
 * <b>that site's membership only</b>, never the pool account (which is the person's account on every site):
 * <ul>
 *   <li>{@code LOCKED} / {@code ACTIVE} → the membership is locked / unlocked ({@link SiteMembershipLockUseCase},
 *       TASK-BE-621). A whole-account lock is not lifted. Any other target → 409.</li>
 *   <li>{@code DELETED} → the membership becomes LEFT ({@code OPERATOR}) ({@link LeaveConsumerSiteUseCase},
 *       TASK-BE-619). A pool account whose membership of the site is already LEFT answers 200, idempotent.</li>
 *   <li>No account row change, no {@code account_status_history} row, no outbox event — the membership row
 *       records the actor. The answer says {@code scope = SITE_MEMBERSHIP}.</li>
 * </ul>
 * A site's OWN account (e.g. ecommerce's seller-operator account — the only production caller's target as of
 * 2026-10-04) and the path {@code consumer-pool} itself change the account as before ({@code scope = ACCOUNT}).
 */
@Service
@RequiredArgsConstructor
public class ProvisionStatusChangeUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountStatusMachine statusMachine;
    private final AccountEventPublisher eventPublisher;
    /** TASK-BE-616 — § 5: the site tenant finds that site's pool members too ({@link SiteAccountLookup}). */
    private final ConsumerPoolFlag consumerPoolFlag;
    /** TASK-BE-622 — the site scope of a pool member's LOCKED / ACTIVE (TASK-BE-621's transition). */
    private final SiteMembershipLockUseCase siteMembershipLockUseCase;
    /** TASK-BE-622 — the site scope of a pool member's DELETED (TASK-BE-619's «사이트 탈퇴», by OPERATOR). */
    private final LeaveConsumerSiteUseCase leaveConsumerSiteUseCase;
    /** TASK-BE-622 — the previous membership status for the answer, and the LEFT-member idempotency read. */
    private final ConsumerSiteMembershipRepository membershipRepository;

    @Transactional
    public ProvisionedStatusChangeResult execute(String tenantIdStr, String accountId,
                                                  AccountStatus targetStatus,
                                                  String operatorId) {
        TenantId tenantId = new TenantId(tenantIdStr);

        // Validate tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantIdStr));

        String actor = operatorId != null ? operatorId : tenantIdStr;

        // Validate account exists within this tenant — TASK-BE-616 (§ 5): or is a pool account that is a
        // member (ACTIVE or LOCKED) of it.
        Optional<Account> found = SiteAccountLookup.find(accountRepository, consumerPoolFlag, tenantId, accountId);
        if (found.isEmpty()) {
            return deletedAgainForPoolMemberThatLeft(tenantId, accountId, targetStatus, actor)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
        }
        Account account = found.get();

        // TASK-BE-622 — a pool member reached through a site: that site's membership only.
        if (account.getTenantId().isConsumerPool() && !tenantId.isConsumerPool()) {
            return changeSiteMembership(tenantId, account, targetStatus, actor);
        }

        AccountStatus previousStatus = account.getStatus();
        StatusTransition transition = account.changeStatus(
                statusMachine, targetStatus, StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE);

        accountRepository.save(account);

        // Audit
        // TASK-BE-616: the history row carries the ACCOUNT's tenant (consumer-pool for a pool account reached
        // through the consumer-pool path; the same value as the path for a site account) — multi-tenancy.md § 3
        // keeps IAM rows on it.
        AccountStatusHistoryEntry historyEntry = AccountStatusHistoryEntry.create(
                account.getTenantId().value(),
                accountId,
                transition.from(),
                transition.to(),
                StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE,
                "provisioning_system",
                actor,
                "{\"action\":\"OPERATOR_PROVISIONING_STATUS_CHANGE\"}"
        );
        historyRepository.save(historyEntry);

        Instant now = Instant.now();

        // Publish outbox events
        AccountStatusEvents.publishStatusChangeEvents(
                eventPublisher, account, previousStatus, targetStatus,
                StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE.name(),
                "provisioning_system", actor, now);

        return new ProvisionedStatusChangeResult(
                accountId,
                tenantIdStr,
                previousStatus.name(),
                account.getStatus().name(),
                now
        );
    }

    /**
     * TASK-BE-622 — the site scope. Writes only the membership (through the 621 / 619 use cases, which hold no
     * account repository write and no event publisher for it).
     */
    private ProvisionedStatusChangeResult changeSiteMembership(TenantId site, Account account,
                                                               AccountStatus targetStatus, String actor) {
        String accountId = account.getId();
        if (targetStatus == AccountStatus.DELETED) {
            String previous = membershipRepository.find(site, accountId)
                    .map(m -> m.getStatus().name())
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            LeaveConsumerSiteResult left = leaveConsumerSiteUseCase.execute(
                    site.value(), accountId, ConsumerSiteLeftBy.OPERATOR, actor);
            return ProvisionedStatusChangeResult.siteMembership(accountId, site.value(), previous,
                    left.membershipStatus(), Instant.now());
        }
        // LOCKED / ACTIVE → the membership; anything else → StateTransitionException (409) in the lock use case.
        StatusChangeResult changed = siteMembershipLockUseCase.execute(
                site, account, targetStatus, StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE, actor);
        return ProvisionedStatusChangeResult.siteMembership(accountId, site.value(), changed.previousStatus(),
                changed.currentStatus(), changed.changedAt());
    }

    /**
     * TASK-BE-622 Edge Case 1 — {@code DELETED} of a pool account whose membership of the path site is already
     * LEFT answers 200 (idempotent: a fail-soft machine caller retries, and a 404 on the retry could not be told
     * apart from «no such member»). It is the site's own membership row that is read — no tenant-less lookup. A
     * self-left row is re-recorded as OPERATOR (TASK-BE-619 — the site's removal sticks). Every other target on a
     * LEFT member stays the 404 the § 5 lookup gives (a LEFT membership is not brought back).
     */
    private Optional<ProvisionedStatusChangeResult> deletedAgainForPoolMemberThatLeft(
            TenantId site, String accountId, AccountStatus targetStatus, String actor) {
        if (targetStatus != AccountStatus.DELETED || !consumerPoolFlag.isEnabled() || site.isConsumerPool()) {
            return Optional.empty();
        }
        Optional<ConsumerSiteMembership> leftMembership = membershipRepository.find(site, accountId)
                .filter(m -> m.getStatus() == ConsumerSiteMembershipStatus.LEFT);
        if (leftMembership.isEmpty() || accountRepository.findById(TenantId.CONSUMER_POOL, accountId).isEmpty()) {
            return Optional.empty();
        }
        LeaveConsumerSiteResult left = leaveConsumerSiteUseCase.execute(
                site.value(), accountId, ConsumerSiteLeftBy.OPERATOR, actor);
        return Optional.of(ProvisionedStatusChangeResult.siteMembership(accountId, site.value(),
                ConsumerSiteMembershipStatus.LEFT.name(), left.membershipStatus(), Instant.now()));
    }
}
