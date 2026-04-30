package com.example.account.application.service;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.ProvisionedStatusChangeResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
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

/**
 * TASK-BE-231: Tenant-scoped account status change for the internal provisioning API.
 *
 * <p>Records audit with {@code OPERATOR_PROVISIONING_STATUS_CHANGE} and publishes
 * the outbox {@code account.status.changed} event with the correct {@code tenant_id}.
 */
@Service
@RequiredArgsConstructor
public class ProvisionStatusChangeUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountStatusMachine statusMachine;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public ProvisionedStatusChangeResult execute(String tenantIdStr, String accountId,
                                                  AccountStatus targetStatus,
                                                  String operatorId) {
        TenantId tenantId = new TenantId(tenantIdStr);

        // Validate tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantIdStr));

        // Validate account exists within this tenant
        Account account = accountRepository.findById(tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        AccountStatus previousStatus = account.getStatus();
        StatusTransition transition = account.changeStatus(
                statusMachine, targetStatus, StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE);

        accountRepository.save(account);

        // Audit
        String actor = operatorId != null ? operatorId : tenantIdStr;
        AccountStatusHistoryEntry historyEntry = AccountStatusHistoryEntry.create(
                tenantIdStr,
                accountId,
                transition.from(),
                transition.to(),
                StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE,
                "provisioning_system",
                actor,
                "action=OPERATOR_PROVISIONING_STATUS_CHANGE"
        );
        historyRepository.save(historyEntry);

        Instant now = Instant.now();

        // Publish outbox events
        eventPublisher.publishStatusChanged(account, previousStatus.name(),
                StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE.name(),
                "provisioning_system", actor, now);

        if (targetStatus == AccountStatus.LOCKED) {
            eventPublisher.publishAccountLocked(account,
                    StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE.name(),
                    "provisioning_system", actor, now);
        } else if (targetStatus == AccountStatus.ACTIVE && previousStatus == AccountStatus.LOCKED) {
            eventPublisher.publishAccountUnlocked(account,
                    StatusChangeReason.OPERATOR_PROVISIONING_STATUS_CHANGE.name(),
                    "provisioning_system", actor, now);
        }

        return new ProvisionedStatusChangeResult(
                accountId,
                tenantIdStr,
                previousStatus.name(),
                account.getStatus().name(),
                now
        );
    }
}
