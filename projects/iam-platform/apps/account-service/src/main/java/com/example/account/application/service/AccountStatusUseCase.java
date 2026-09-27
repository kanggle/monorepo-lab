package com.example.account.application.service;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.result.AccountStatusResult;
import com.example.account.application.result.AccountStatusWithTenantResult;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.status.*;
import com.example.account.domain.tenant.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AccountStatusUseCase {

    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountStatusMachine statusMachine;
    private final AccountEventPublisher eventPublisher;
    private final int gracePeriodDays;

    public AccountStatusUseCase(AccountRepository accountRepository,
                                 AccountStatusHistoryRepository historyRepository,
                                 AccountStatusMachine statusMachine,
                                 AccountEventPublisher eventPublisher,
                                 @Value("${account.deletion.grace-period-days:30}") int gracePeriodDays) {
        this.accountRepository = accountRepository;
        this.historyRepository = historyRepository;
        this.statusMachine = statusMachine;
        this.eventPublisher = eventPublisher;
        this.gracePeriodDays = gracePeriodDays;
    }

    /**
     * NET-ZERO overload — a header-less caller stays pinned to {@link TenantId#FAN_PLATFORM},
     * byte-identical to the pre-BE-507 behaviour.
     */
    @Transactional(readOnly = true)
    public AccountStatusResult getStatus(String accountId) {
        return getStatus(accountId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-507 — tenant-aware status read (the gateway-propagated {@code X-Tenant-Id}).
     */
    @Transactional(readOnly = true)
    public AccountStatusResult getStatus(String accountId, TenantId tenantId) {
        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        var latestHistory = historyRepository.findTopByAccountIdOrderByOccurredAtDesc(accountId);

        return new AccountStatusResult(
                account.getId(),
                account.getStatus().name(),
                latestHistory.map(AccountStatusHistoryEntry::getOccurredAt).orElse(account.getCreatedAt()),
                latestHistory.map(h -> h.getReasonCode().name()).orElse(null)
        );
    }

    /**
     * TASK-BE-602 — status AND the account's own tenant, looked up by id alone.
     *
     * <p>For the social-login callback, which knows the account id but not reliably its tenant:
     * the initiating client's tenant and the social-identity row's tenant are both wrong for
     * accounts born before TASK-BE-507 (identity {@code ecommerce}, account {@code fan-platform}).
     * The tenant is returned, never taken as input — the documented exception in
     * {@link AccountRepository#findByIdResolvingTenant(String)}.
     *
     * @throws AccountNotFoundException when no tenant holds an account with this id (→ 404)
     */
    @Transactional(readOnly = true)
    public AccountStatusWithTenantResult getStatusResolvingTenant(String accountId) {
        Account account = accountRepository.findByIdResolvingTenant(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        var latestHistory = historyRepository.findTopByAccountIdOrderByOccurredAtDesc(accountId);

        return new AccountStatusWithTenantResult(
                account.getId(),
                account.getTenantId().value(),
                account.getStatus().name(),
                latestHistory.map(AccountStatusHistoryEntry::getOccurredAt).orElse(account.getCreatedAt())
        );
    }

    /**
     * NET-ZERO overload — header-less / batch callers (e.g. the dormant scheduler)
     * stay pinned to {@link TenantId#FAN_PLATFORM}, byte-identical to today.
     */
    @Transactional
    public StatusChangeResult changeStatus(ChangeStatusCommand command) {
        return changeStatus(command, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-467 — tenant-aware lock/unlock/status-change. The admin mutation path
     * threads the actor's active tenant here; a cross-tenant target resolves through
     * the tenant-scoped {@code findById} to a 404 (enumeration-safe confinement).
     */
    @Transactional
    public StatusChangeResult changeStatus(ChangeStatusCommand command, TenantId tenantId) {
        Account account = accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));
        return applyStatusChange(account, command);
    }

    /**
     * TASK-MONO-735 — lock/unlock for a caller that did NOT name a tenant (internal
     * {@code /lock} / {@code /unlock} with no, blank or {@code "*"} {@code X-Tenant-Id}):
     * the target is found in the tenant its own row lives in, via the documented exception
     * {@link AccountRepository#findByIdResolvingTenant(String)}.
     *
     * <p>Until MONO-735 such a caller was pinned to {@code fan-platform}, so the security-service
     * auto-lock and a SUPER_ADMIN ({@code "*"}) console lock returned 404 for every account outside
     * it (measured live 2026-09-26 on an {@code ecommerce} account). A caller that DOES name a
     * tenant keeps {@link #changeStatus(ChangeStatusCommand, TenantId)} — confined, cross-tenant → 404.
     *
     * @throws AccountNotFoundException when no tenant holds an account with this id (→ 404)
     */
    @Transactional
    public StatusChangeResult changeStatusResolvingTenant(ChangeStatusCommand command) {
        Account account = accountRepository.findByIdResolvingTenant(command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));
        return applyStatusChange(account, command);
    }

    private StatusChangeResult applyStatusChange(Account account, ChangeStatusCommand command) {
        AccountStatus previousStatus = account.getStatus();
        StatusTransition transition = account.changeStatus(
                statusMachine, command.targetStatus(), command.reason());

        accountRepository.save(account);

        recordStatusHistory(account.getId(), transition, command.actorType(), command.actorId(), command.details());

        Instant now = Instant.now();
        publishStatusChangeEvents(account, previousStatus, command, now);

        return new StatusChangeResult(
                account.getId(),
                previousStatus.name(),
                account.getStatus().name(),
                now
        );
    }

    private void recordStatusHistory(String accountId, StatusTransition transition,
                                      String actorType, String actorId, String details) {
        AccountStatusHistoryEntry historyEntry = AccountStatusHistoryEntry.create(
                accountId,
                transition.from(),
                transition.to(),
                transition.reason(),
                actorType,
                actorId,
                details
        );
        historyRepository.save(historyEntry);
    }

    private void publishStatusChangeEvents(Account account, AccountStatus previousStatus,
                                            ChangeStatusCommand command, Instant now) {
        if (previousStatus == command.targetStatus()) {
            return;
        }

        AccountStatusEvents.publishStatusChangeEvents(
                eventPublisher, account, previousStatus, command.targetStatus(),
                command.reason().name(), command.actorType(), command.actorId(), now);
    }

    /**
     * NET-ZERO overload — consumer self-deletion (public status controller) stays
     * pinned to {@link TenantId#FAN_PLATFORM}, byte-identical to today.
     */
    @Transactional
    public DeleteAccountResult deleteAccount(String accountId, StatusChangeReason reason,
                                              String actorType, String actorId) {
        return deleteAccount(accountId, reason, actorType, actorId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-467 — tenant-aware operator delete. Cross-tenant target → 404 via the
     * tenant-scoped {@code findById} (enumeration-safe confinement).
     */
    @Transactional
    public DeleteAccountResult deleteAccount(String accountId, StatusChangeReason reason,
                                              String actorType, String actorId, TenantId tenantId) {
        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return applyDelete(account, reason, actorType, actorId);
    }

    /**
     * TASK-MONO-735 — operator delete for a caller that did NOT name a tenant (internal
     * {@code /delete} with no, blank or {@code "*"} {@code X-Tenant-Id}); same rule as
     * {@link #changeStatusResolvingTenant(ChangeStatusCommand)}.
     *
     * @throws AccountNotFoundException when no tenant holds an account with this id (→ 404)
     */
    @Transactional
    public DeleteAccountResult deleteAccountResolvingTenant(String accountId, StatusChangeReason reason,
                                                             String actorType, String actorId) {
        Account account = accountRepository.findByIdResolvingTenant(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return applyDelete(account, reason, actorType, actorId);
    }

    private DeleteAccountResult applyDelete(Account account, StatusChangeReason reason,
                                            String actorType, String actorId) {
        AccountStatus previousStatus = account.getStatus();
        StatusTransition transition = account.changeStatus(statusMachine, AccountStatus.DELETED, reason);

        accountRepository.save(account);

        recordStatusHistory(account.getId(), transition, actorType, actorId, null);

        Instant now = Instant.now();
        Instant gracePeriodEndsAt = now.plus(gracePeriodDays, ChronoUnit.DAYS);

        eventPublisher.publishStatusChanged(
                account, account.getTenantId().value(), previousStatus.name(), reason.name(),
                actorType, actorId, now);

        eventPublisher.publishAccountDeleted(
                account, account.getTenantId().value(), reason.name(), actorType, actorId,
                now, gracePeriodEndsAt);

        return new DeleteAccountResult(
                account.getId(),
                previousStatus.name(),
                AccountStatus.DELETED.name(),
                gracePeriodEndsAt
        );
    }

}
