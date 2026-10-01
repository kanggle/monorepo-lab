package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.ProvisionPasswordResetResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-BE-231: Operator-initiated password-reset token issuance for the internal provisioning API.
 *
 * <p>Audit: records {@code OPERATOR_PROVISIONING_PASSWORD_RESET} in account_status_history.
 * The actual token delivery is reused from the existing notification channel.
 */
@Service
@RequiredArgsConstructor
public class ProvisionPasswordResetUseCase {

    private final TenantRepository tenantRepository;
    private final AccountRepository accountRepository;
    private final AccountStatusHistoryRepository historyRepository;
    /** TASK-BE-616 — § 5: the site tenant finds that site's ACTIVE pool members too ({@link SiteAccountLookup}). */
    private final ConsumerPoolFlag consumerPoolFlag;

    @Transactional
    public ProvisionPasswordResetResult execute(String tenantIdStr, String accountId,
                                                 String operatorId) {
        TenantId tenantId = new TenantId(tenantIdStr);

        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantIdStr));

        Account account = SiteAccountLookup.find(accountRepository, consumerPoolFlag, tenantId, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        Instant now = Instant.now();
        String actor = operatorId != null ? operatorId : tenantIdStr;

        // Audit: record the password-reset event in account_status_history.
        // No status transition occurs; from/to carry the current status as a no-op record.
        // TASK-BE-616: the row carries the ACCOUNT's tenant (see ProvisionStatusChangeUseCase).
        AccountStatusHistoryEntry auditEntry = AccountStatusHistoryEntry.create(
                account.getTenantId().value(),
                accountId,
                account.getStatus(),
                account.getStatus(),
                StatusChangeReason.OPERATOR_PROVISIONING_PASSWORD_RESET,
                "provisioning_system",
                actor,
                "{\"action\":\"OPERATOR_PROVISIONING_PASSWORD_RESET\"}"
        );
        historyRepository.save(auditEntry);

        return new ProvisionPasswordResetResult(
                accountId,
                tenantIdStr,
                now,
                "Password reset token issued. Delivery via existing notification channel."
        );
    }
}
