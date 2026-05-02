package com.example.account.application.service;

import com.example.account.application.command.RemoveAccountRoleCommand;
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
 * TASK-BE-255: Remove a single role from an account.
 *
 * <p>Idempotent: removing a role that the account does not have is a successful
 * no-op (the response carries the unchanged role list and {@code changed=false}).
 * Only actual deletions record an audit row and publish the
 * {@code account.roles.changed} outbox event.
 */
@Service
@RequiredArgsConstructor
public class RemoveAccountRoleUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public AccountRoleMutationResult execute(RemoveAccountRoleCommand command) {
        TenantId tenantId = new TenantId(command.tenantId());

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));

        Account account = accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        // The role-name must still be syntactically valid even when removing — guards
        // against typos that would otherwise silently no-op.
        AccountRoleName.validate(command.roleName());

        List<String> beforeRoles = new ArrayList<>(
                accountRoleRepository.findByTenantIdAndAccountId(tenantId, command.accountId())
                        .stream().map(AccountRole::getRoleName).toList()
        );

        boolean deleted = accountRoleRepository.removeIfPresent(
                tenantId, command.accountId(), command.roleName());

        Instant now = Instant.now();

        if (!deleted) {
            return new AccountRoleMutationResult(
                    command.accountId(), command.tenantId(), beforeRoles, now, false);
        }

        List<String> afterRoles = new ArrayList<>(beforeRoles);
        afterRoles.remove(command.roleName());

        String operatorId = command.operatorId() != null ? command.operatorId() : command.tenantId();
        AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                command.tenantId(),
                command.accountId(),
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE,
                "provisioning_system",
                operatorId,
                "action=ROLE_REMOVE,role=" + command.roleName()
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
