package com.example.account.application.service;

import com.example.account.application.command.BulkProvisionAccountCommand;
import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.BulkLimitExceededException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.result.BulkProvisionAccountResult;
import com.example.account.application.result.BulkProvisionAccountResult.CreatedItem;
import com.example.account.application.result.BulkProvisionAccountResult.FailedItem;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-257: Bulk provisioning use case.
 *
 * <p>Processes up to 1 000 account creation items in a single HTTP request.
 * Uses a <strong>partial-success</strong> model: each row is executed in its own
 * nested {@code REQUIRES_NEW} transaction so that one row's failure (e.g.
 * {@code EMAIL_DUPLICATE}) does not roll back other rows.
 *
 * <p>Per-row provisioning logic is fully delegated to
 * {@link ProvisionAccountUseCase#execute(ProvisionAccountCommand)} — no
 * duplication of provisioning business rules here.
 *
 * <p>After all rows are processed, one audit row is written to
 * {@code account_status_history} recording the bulk call as a whole
 * ({@code action=ACCOUNT_BULK_CREATE,targetCount=N}).
 *
 * <p><b>Audit obligation (admin-service):</b> The {@code admin_actions} table in
 * admin-service is not written by account-service directly. An
 * {@code ACCOUNT_BULK_CREATE} action code should be emitted via admin-service's
 * audit emission pattern once that pattern is established for provisioning flows.
 * Until then, the audit is captured in {@code account_status_history} only.
 * TODO(TASK-BE-257): wire admin-service audit event when provisioning audit
 * emission pattern is defined.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkProvisionAccountUseCase {

    static final int MAX_BULK_SIZE = 1000;

    private final TenantRepository tenantRepository;
    private final AccountStatusHistoryRepository historyRepository;
    private final RowProvisioningHelper rowProvisioningHelper;

    /**
     * Execute a bulk provisioning request.
     *
     * <p>Validates the tenant up front (tenant must exist and be ACTIVE) before
     * iterating rows. Each row is delegated to {@link RowProvisioningHelper#provisionRow}
     * which runs in its own transaction ({@code REQUIRES_NEW}).
     *
     * @param command the bulk command carrying all items
     * @return partial-success result with per-row outcomes
     * @throws BulkLimitExceededException if items.size() > 1 000
     * @throws TenantNotFoundException    if the tenant does not exist
     * @throws TenantSuspendedException   if the tenant is SUSPENDED
     */
    @Transactional
    public BulkProvisionAccountResult execute(BulkProvisionAccountCommand command) {
        List<BulkProvisionAccountCommand.Item> items =
                command.items() != null ? command.items() : List.of();

        // Guard: max 1000 items
        if (items.size() > MAX_BULK_SIZE) {
            throw new BulkLimitExceededException(items.size(), MAX_BULK_SIZE);
        }

        // Guard: tenant must exist and be ACTIVE before processing any row
        TenantId tenantId = new TenantId(command.tenantId());
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(command.tenantId()));
        if (!tenant.isActive()) {
            throw new TenantSuspendedException(command.tenantId());
        }

        // Process rows — each in its own REQUIRES_NEW transaction
        List<CreatedItem> created = new ArrayList<>();
        List<FailedItem> failed = new ArrayList<>();

        for (BulkProvisionAccountCommand.Item item : items) {
            try {
                ProvisionAccountResult rowResult = rowProvisioningHelper.provisionRow(
                        command.tenantId(), item, command.operatorId());
                created.add(new CreatedItem(item.externalId(), rowResult.accountId()));
            } catch (AccountAlreadyExistsException e) {
                log.debug("[bulk] email duplicate for externalId={}: {}", item.externalId(), e.getMessage());
                failed.add(new FailedItem(item.externalId(), "EMAIL_DUPLICATE",
                        "Email already exists within the tenant: " + item.email()));
            } catch (IllegalArgumentException e) {
                // role name validation failure surfaces as IllegalArgumentException from AccountRoleName.validate
                log.debug("[bulk] validation error for externalId={}: {}", item.externalId(), e.getMessage());
                String code = e.getMessage() != null && e.getMessage().contains("roleName") ? "INVALID_ROLE" : "VALIDATION_ERROR";
                failed.add(new FailedItem(item.externalId(), code, e.getMessage()));
            } catch (Exception e) {
                log.warn("[bulk] unexpected error for externalId={}: {}", item.externalId(), e.getMessage());
                failed.add(new FailedItem(item.externalId(), "VALIDATION_ERROR", e.getMessage()));
            }
        }

        // Write one audit row for the entire bulk call
        if (!items.isEmpty()) {
            writeBulkAuditEntry(command.tenantId(), command.operatorId(), created.size());
        }

        return BulkProvisionAccountResult.of(created, failed, items.size());
    }

    private void writeBulkAuditEntry(String tenantId, String operatorId, int createdCount) {
        String actorId = operatorId != null ? operatorId : tenantId;
        AccountStatusHistoryEntry entry = AccountStatusHistoryEntry.create(
                tenantId,
                tenantId,                                     // accountId field reused for bulk-level record
                AccountStatus.ACTIVE,
                AccountStatus.ACTIVE,
                StatusChangeReason.OPERATOR_PROVISIONING_CREATE,
                "provisioning_system",
                actorId,
                "action=ACCOUNT_BULK_CREATE,targetCount=" + createdCount
        );
        historyRepository.save(entry);
    }
}
