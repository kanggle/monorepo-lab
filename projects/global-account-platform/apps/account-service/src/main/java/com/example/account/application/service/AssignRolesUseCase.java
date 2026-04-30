package com.example.account.application.service;

import com.example.account.application.command.AssignRolesCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.AssignRolesResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
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
import java.util.List;

/**
 * TASK-BE-231: Replaces all roles for an account within a tenant.
 *
 * <p>Deletes existing role assignments and inserts the new set in a single transaction.
 * Publishes outbox {@code account.roles.changed} event and records audit entry
 * with {@code OPERATOR_PROVISIONING_ROLES_REPLACE} action code.
 */
@Service
@RequiredArgsConstructor
public class AssignRolesUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final AccountEventPublisher eventPublisher;

    @Transactional
    public AssignRolesResult execute(AssignRolesCommand command) {
        TenantId tenantId = new TenantId(command.tenantId());

        // Validate tenant exists
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));

        // Validate account exists within this tenant
        Account account = accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        // Replace roles atomically
        accountRoleRepository.deleteAllByTenantIdAndAccountId(tenantId, command.accountId());

        List<String> newRoles = command.roles() != null ? command.roles() : List.of();
        for (String roleName : newRoles) {
            AccountRole role = AccountRole.create(tenantId, command.accountId(), roleName);
            accountRoleRepository.save(role);
        }

        // Audit record
        String operatorId = command.operatorId() != null ? command.operatorId() : command.tenantId();
        AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                command.tenantId(),
                command.accountId(),
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE,
                "provisioning_system",
                operatorId,
                "action=OPERATOR_PROVISIONING_ROLES_REPLACE,roles=" + newRoles
        );
        historyRepository.save(auditEntry);

        // Publish outbox event
        Instant now = Instant.now();
        eventPublisher.publishRolesChanged(account, newRoles, "provisioning_system", operatorId, now);

        return new AssignRolesResult(command.accountId(), command.tenantId(), newRoles, now);
    }
}
