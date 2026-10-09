package com.example.auth.domain.repository;

import com.example.auth.domain.mfa.AccountTotp;

import java.util.Optional;

/**
 * TASK-MONO-771 — port for {@code account_totp}. Keyed and looked up by {@code account_id} ONLY
 * (data-model.md § account_totp — {@code tenant_id} is never a lookup predicate).
 */
public interface AccountTotpRepository {

    Optional<AccountTotp> findByAccountId(String accountId);

    /**
     * Inserts or updates the row. A concurrent writer that changed the row first makes this throw (optimistic
     * lock, T5) — the second of two simultaneous submissions of the same code loses.
     */
    AccountTotp save(AccountTotp totp);

    /** Removes the account's row (replacing a pending one). No-op when there is none. */
    void deleteByAccountId(String accountId);
}
