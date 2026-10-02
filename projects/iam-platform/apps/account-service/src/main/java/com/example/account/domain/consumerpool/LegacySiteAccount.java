package com.example.account.domain.consumerpool;

import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-618 (multi-tenancy.md § 소비자 계정 풀 § 3) — the locked snapshot of one ADR-MONO-078-era
 * <b>site account</b> (an account whose tenant is a consumer site) that the legacy mover is about to move
 * into the pool under the same id. Read with a row lock, so the values cannot change before the move
 * commits.
 *
 * @param accountId    the account id (kept by the move)
 * @param siteTenantId the consumer site the account lives in now
 * @param email        the login email — used only for the coexistence checks, never logged or reported
 * @param status       the account status (moved as is; DELETED accounts are not candidates)
 * @param identityId   the central identity, or {@code null}
 * @param createdAt    the account's creation time — the membership's {@code consented_at} (copied in SQL)
 */
public record LegacySiteAccount(String accountId, TenantId siteTenantId, String email,
                                AccountStatus status, String identityId, Instant createdAt) {

    public LegacySiteAccount {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(siteTenantId, "siteTenantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
