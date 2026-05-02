package com.example.account.application.service;

import com.example.account.application.command.AddAccountRoleCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.AccountRoleMutationResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.account.AccountRoleName;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-255: Add a single role to an account.
 *
 * <p>Idempotent: re-adding an already-present role is a successful no-op (the
 * response carries the unchanged role list and {@code changed=false}). Only
 * actual mutations record an audit row and publish the
 * {@code account.roles.changed} outbox event.
 */
@Service
@RequiredArgsConstructor
public class AddAccountRoleUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public AccountRoleMutationResult execute(AddAccountRoleCommand command) {
        TenantId tenantId = new TenantId(command.tenantId());

        // Tenant must exist (account-service is the source of truth).
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));

        // Cross-tenant safety: findById(tenant, id) returns empty for foreign accounts.
        Account account = accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        AccountRoleName.validate(command.roleName());

        List<String> beforeRoles = new ArrayList<>(
                accountRoleRepository.findByTenantIdAndAccountId(tenantId, command.accountId())
                        .stream().map(AccountRole::getRoleName).toList()
        );

        String operatorId = command.operatorId() != null ? command.operatorId() : command.tenantId();
        AccountRole role = AccountRole.create(tenantId, command.accountId(), command.roleName(), operatorId);
        boolean inserted = accountRoleRepository.addIfAbsent(role);

        Instant now = Instant.now();

        if (!inserted) {
            // Idempotent no-op: surface the unchanged list, do not audit, do not emit event.
            return new AccountRoleMutationResult(
                    command.accountId(), command.tenantId(), beforeRoles, now, false);
        }

        List<String> afterRoles = new ArrayList<>(beforeRoles);
        afterRoles.add(command.roleName());

        AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                command.tenantId(),
                command.accountId(),
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE,
                "provisioning_system",
                operatorId,
                "action=ROLE_ADD,role=" + command.roleName()
                        + ",before=" + beforeRoles + ",after=" + afterRoles
        );
        historyRepository.save(auditEntry);

        eventPublisher.publishRolesChanged(account, account.getTenantId().value(),
                beforeRoles, afterRoles, operatorId,
                "provisioning_system", operatorId, now);

        return new AccountRoleMutationResult(
                command.accountId(), command.tenantId(), afterRoles, now, true);
    }
}
