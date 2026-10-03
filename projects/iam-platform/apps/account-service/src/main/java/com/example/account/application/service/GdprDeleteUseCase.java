package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.GdprDeleteResult;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.application.util.DigestUtils;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.AccountStatusMachine;
import com.example.account.domain.status.StateTransitionException;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * GDPR/PIPA Right to Erasure use case.
 * Transitions account to DELETED status and immediately masks all PII.
 */
@Service
@RequiredArgsConstructor
public class GdprDeleteUseCase {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountStatusMachine statusMachine;
    private final AccountEventPublisher eventPublisher;
    /** TASK-BE-616 — § 5: the site tenant finds that site's ACTIVE pool members too ({@link SiteAccountLookup}). */
    private final ConsumerPoolFlag consumerPoolFlag;
    /** TASK-BE-619 — what a site operator's erasure of a pool member becomes: that site's membership LEFT. */
    private final LeaveConsumerSiteUseCase leaveConsumerSiteUseCase;

    /**
     * NET-ZERO overload — header-less callers stay pinned to
     * {@link TenantId#FAN_PLATFORM}, byte-identical to today.
     */
    @Transactional
    public GdprDeleteResult execute(String accountId, String operatorId) {
        return execute(accountId, operatorId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-467 — tenant-aware GDPR erasure by a caller that NAMES a tenant (a site operator's active
     * tenant). Cross-tenant target → 404 via the tenant-scoped {@code findById} (enumeration-safe confinement).
     *
     * <p>TASK-BE-619 (owner decision 2026-10-03 «사이트 운영자 삭제 권한 = 자기 사이트 멤버십만»): when the
     * target is a consumer-POOL account found through a site ({@link SiteAccountLookup} — an ACTIVE member of
     * that site), the request does NOT erase the account. One site can never delete another site's member
     * data, and the pool account is every site's. It ends THAT site's membership instead
     * ({@link LeaveConsumerSiteUseCase}, {@code left_by = OPERATOR} — consent cannot reopen it) and answers
     * {@code scope = SITE_MEMBERSHIP}. Erasing the pool account is {@link #executeResolvingTenant} (platform
     * admin) or the person's own {@code DELETE /api/accounts/me}. Until 619 (TASK-BE-616) this path erased
     * the whole pool account — on every consumer site.
     *
     * <p>A site's OWN account (not pooled) is erased as before — it belongs to that site alone.
     */
    @Transactional
    public GdprDeleteResult execute(String accountId, String operatorId, TenantId tenantId) {
        Account account = SiteAccountLookup.find(accountRepository, consumerPoolFlag, tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        if (account.getTenantId().isConsumerPool() && !tenantId.isConsumerPool()) {
            LeaveConsumerSiteResult left = leaveConsumerSiteUseCase.execute(
                    tenantId.value(), accountId, ConsumerSiteLeftBy.OPERATOR, operatorId);
            return GdprDeleteResult.siteMembershipLeft(accountId, left.accountStatus(), tenantId.value());
        }
        return erase(account, operatorId);
    }

    /**
     * TASK-BE-619 — GDPR erasure by a caller that names NO tenant ({@code X-Tenant-Id} absent, blank or the
     * SUPER_ADMIN platform scope {@code "*"}): the platform admin. The target is found in the tenant its own
     * row lives in (the documented exception {@link AccountRepository#findByIdResolvingTenant}, its third
     * consumer — multi-tenancy.md § 격리 회귀 방지), and the account itself is erased — for a pool account,
     * on every consumer site. Same rule as {@code /lock}, {@code /unlock}, {@code /delete} (TASK-MONO-735).
     *
     * @throws AccountNotFoundException when no tenant holds an account with this id (→ 404)
     */
    @Transactional
    public GdprDeleteResult executeResolvingTenant(String accountId, String operatorId) {
        Account account = accountRepository.findByIdResolvingTenant(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return erase(account, operatorId);
    }

    private GdprDeleteResult erase(Account account, String operatorId) {
        String accountId = account.getId();
        AccountStatus previousStatus = account.getStatus();

        // Spec: contracts/http/internal/admin-to-account.md POST /gdpr-delete returns
        // STATE_TRANSITION_INVALID for already-DELETED accounts. The shared
        // AccountStatusMachine treats same-state transitions idempotently for lock
        // operations, so guard explicitly here for the GDPR erasure path which is
        // not idempotent — masking a re-masked email would corrupt email_hash.
        if (previousStatus == AccountStatus.DELETED) {
            throw new StateTransitionException(
                    AccountStatus.DELETED,
                    AccountStatus.DELETED,
                    StatusChangeReason.REGULATED_DELETION);
        }

        // Transition to DELETED via state machine
        account.changeStatus(statusMachine, AccountStatus.DELETED, StatusChangeReason.REGULATED_DELETION);

        // Mask email: replace with hash-based value
        String emailHash = DigestUtils.sha256Hex(account.getEmail());
        String maskedEmail = "gdpr_" + emailHash + "@deleted.local";
        account.maskEmail(emailHash, maskedEmail);

        accountRepository.save(account);

        // Record status history
        AccountStatusHistoryEntry historyEntry = AccountStatusHistoryEntry.create(
                account.getId(),
                previousStatus,
                AccountStatus.DELETED,
                StatusChangeReason.REGULATED_DELETION,
                "operator",
                operatorId,
                "{\"action\":\"REGULATED_DELETION\",\"note\":\"GDPR deletion with immediate PII masking\"}"
        );
        historyRepository.save(historyEntry);

        // Mask profile PII
        Instant maskedAt = Instant.now();
        profileRepository.findByAccountId(accountId).ifPresent(profile -> {
            profile.maskPii();
            profileRepository.save(profile);
        });

        // Publish events
        Instant now = Instant.now();
        eventPublisher.publishStatusChanged(
                account, account.getTenantId().value(), previousStatus.name(),
                StatusChangeReason.REGULATED_DELETION.name(), "operator", operatorId, now);

        // GDPR/PIPA Right to Erasure path is *immediate* (no grace period), so
        // gracePeriodEndsAt collapses onto the deletion instant — see retention.md §2.2.
        eventPublisher.publishAccountDeletedAnonymized(
                account, account.getTenantId().value(), StatusChangeReason.REGULATED_DELETION.name(),
                "operator", operatorId, now, now);

        return new GdprDeleteResult(account.getId(), AccountStatus.DELETED.name(), emailHash, maskedAt);
    }

}
