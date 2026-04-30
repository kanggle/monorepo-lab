package com.example.account.application.result;

import com.example.account.domain.account.Account;

import java.time.Instant;
import java.util.List;

/**
 * TASK-BE-231: Single-account detail result for the provisioning GET endpoint.
 * Excludes password_hash, deleted_at, email_hash per regulated trait R4.
 */
public record ProvisionedAccountDetailResult(
        String accountId,
        String tenantId,
        String email,
        String status,
        List<String> roles,
        String displayName,
        Instant createdAt,
        Instant emailVerifiedAt
) {
    public static ProvisionedAccountDetailResult from(Account account, String displayName, List<String> roles) {
        return new ProvisionedAccountDetailResult(
                account.getId(),
                account.getTenantId().value(),
                account.getEmail(),
                account.getStatus().name(),
                roles,
                displayName,
                account.getCreatedAt(),
                account.getEmailVerifiedAt()
        );
    }
}
