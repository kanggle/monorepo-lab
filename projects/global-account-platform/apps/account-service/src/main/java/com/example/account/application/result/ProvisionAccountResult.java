package com.example.account.application.result;

import com.example.account.domain.account.Account;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Result of a successful account provisioning operation.
 * Sensitive fields (password_hash, deleted_at, email_hash) are excluded.
 */
public record ProvisionAccountResult(
        String accountId,
        String tenantId,
        String email,
        String status,
        List<String> roles,
        Instant createdAt
) {
    public static ProvisionAccountResult from(Account account, List<String> roles) {
        return new ProvisionAccountResult(
                account.getId(),
                account.getTenantId().value(),
                account.getEmail(),
                account.getStatus().name(),
                roles,
                account.getCreatedAt()
        );
    }
}
