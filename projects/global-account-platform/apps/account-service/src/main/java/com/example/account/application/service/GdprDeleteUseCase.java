package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.GdprDeleteResult;
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

    @Transactional
    public GdprDeleteResult execute(String accountId, String operatorId) {
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229
        Account account = accountRepository.findById(TenantId.FAN_PLATFORM, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

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
                "GDPR deletion with immediate PII masking"
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
