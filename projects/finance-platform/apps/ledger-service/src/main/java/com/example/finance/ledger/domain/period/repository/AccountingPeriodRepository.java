package com.example.finance.ledger.domain.period.repository;

import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.example.finance.ledger.domain.period.PeriodBalanceSnapshot;
import com.example.finance.ledger.domain.period.PeriodStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for accounting-period persistence (architecture.md § Layer
 * Structure). Periods are tenant-scoped; the {@code findOverlapping} /
 * {@code findCovering} queries back the non-overlap invariant and the posting
 * guard. The close-time {@link PeriodBalanceSnapshot} is insert-only (F3/F6
 * parity). Implemented by an infrastructure JPA adapter.
 */
public interface AccountingPeriodRepository {

    AccountingPeriod save(AccountingPeriod period);

    Optional<AccountingPeriod> findById(String periodId, String tenantId);

    /** All periods for a tenant, most-recent window first. */
    List<AccountingPeriod> findAll(String tenantId);

    /** Periods whose window overlaps {@code [from, to)} (non-overlap check on open). */
    List<AccountingPeriod> findOverlapping(String tenantId, Instant from, Instant to);

    /**
     * The period of the given {@code status} whose window covers {@code postedAt}
     * ({@code from ≤ postedAt < to}). Used by the posting guard with
     * {@code status = CLOSED}; empty = net-zero (posting proceeds).
     */
    Optional<AccountingPeriod> findCovering(String tenantId, Instant postedAt, PeriodStatus status);

    /** Persist the close-time snapshot rows (insert-only) for a period. */
    void saveSnapshot(String periodId, String tenantId, PeriodBalanceSnapshot snapshot);

    /** The persisted snapshot for a CLOSED period (empty while OPEN / absent). */
    Optional<PeriodBalanceSnapshot> findSnapshot(String periodId, String tenantId);
}
