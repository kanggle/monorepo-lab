package com.example.auth.domain.repository;

import com.example.auth.domain.mfa.AccountTotp;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

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

    /**
     * TASK-MONO-771 S5 — of {@code accountIds}, the ones with a CONFIRMED enrolment (a pending row from an
     * unfinished {@code /mfa/setup} does not count: that account still has no second factor). Feeds the entry-policy
     * pre-check (admin-to-auth.md § second-factor enrolment status). Never {@code null}.
     *
     * <p>The default is the obvious per-id loop; the JPA adapter overrides it with one {@code IN} read.
     */
    default Set<String> findConfirmedAccountIds(Collection<String> accountIds) {
        Set<String> confirmed = new LinkedHashSet<>();
        for (String id : accountIds) {
            findByAccountId(id).filter(AccountTotp::isConfirmed).ifPresent(t -> confirmed.add(id));
        }
        return confirmed;
    }
}
