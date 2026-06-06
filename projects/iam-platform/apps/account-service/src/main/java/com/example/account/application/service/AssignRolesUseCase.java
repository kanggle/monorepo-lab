package com.example.account.application.service;

import com.example.account.application.command.AssignRolesCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.AssignRolesResult;
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
 * TASK-BE-231: Replaces all roles for an account within a tenant.
 *
 * <p>Deletes existing role assignments and inserts the new set in a single transaction.
 * Publishes outbox {@code account.roles.changed} event and records audit entry
 * with {@code OPERATOR_PROVISIONING_ROLES_REPLACE} action code.
 *
 * <p>TASK-BE-255: Snapshots the previous role set for the {@code beforeRoles}
 * field on the outbox event and validates each new role name against
 * {@link AccountRoleName} before persisting. Cross-tenant safety is preserved
 * because {@code AccountRepository.findById(tenantId, accountId)} returns empty
 * when the accountId belongs to a different tenant — the call surfaces as
 * {@code ACCOUNT_NOT_FOUND}.
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

        // Validate account exists within this tenant — cross-tenant accountIds surface as 404.
        Account account = accountRepository.findById(tenantId, command.accountId())
                .orElseThrow(() -> new AccountNotFoundException(command.accountId()));

        List<String> requestedRoles = command.roles() != null ? command.roles() : List.of();
        // Up-front validation so a partial-write trajectory is not possible.
        for (String roleName : requestedRoles) {
            AccountRoleName.validate(roleName);
        }

        // Snapshot the existing role set BEFORE mutating, so the outbox event carries beforeRoles.
        List<String> beforeRoles = new ArrayList<>(
                accountRoleRepository.findByTenantIdAndAccountId(tenantId, command.accountId())
                        .stream()
                        .map(AccountRole::getRoleName)
                        .toList()
        );

        // Replace roles atomically.
        accountRoleRepository.deleteAllByTenantIdAndAccountId(tenantId, command.accountId());

        String operatorId = command.operatorId() != null ? command.operatorId() : command.tenantId();
        for (String roleName : requestedRoles) {
            AccountRole role = AccountRole.create(tenantId, command.accountId(), roleName, operatorId);
            accountRoleRepository.save(role);
        }

        // Audit record — details must be valid JSON for the account_status_history.details JSON column.
        String beforeJson = toJsonStringArray(beforeRoles);
        String afterJson = toJsonStringArray(requestedRoles);
        AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                command.tenantId(),
                command.accountId(),
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_ROLES_REPLACE,
                "provisioning_system",
                operatorId,
                "{\"action\":\"OPERATOR_PROVISIONING_ROLES_REPLACE\",\"before\":" + beforeJson + ",\"after\":" + afterJson + "}"
        );
        historyRepository.save(auditEntry);

        // Publish outbox event with both before / after snapshots (TASK-BE-255).
        Instant now = Instant.now();
        eventPublisher.publishRolesChanged(account, account.getTenantId().value(),
                beforeRoles, requestedRoles, operatorId,
                "provisioning_system", operatorId, now);

        return new AssignRolesResult(command.accountId(), command.tenantId(), requestedRoles, now);
    }

    private static String toJsonStringArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
