package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * One acquisition lot of a foreign-currency position (16th increment —
 * TASK-FIN-BE-024, architecture.md § FX position lots, ADR-001 D2/D5). A lot is
 * the materialized record of a single foreign acquisition: the foreign quantity
 * {@code original_foreign_minor} taken on at a base-currency (KRW) cost
 * {@code original_base_minor}. {@code remaining_foreign_minor} /
 * {@code carrying_base_minor} are the still-open portion (FIN-BE-025 will walk
 * them FIFO on settlement); in this increment they always equal the originals —
 * <b>nothing consumes a lot yet</b> (shadow / write-only).
 *
 * <p>Lots are created two ways: (a) the {@link com.example.finance.ledger.application.RecordFxAcquisitionLots}
 * hook fires inside the guarded write path for every position-increasing foreign
 * line of a freshly-posted entry; (b) the V10 migration backfills a single
 * synthetic lot per open pre-existing foreign position (its carrying matches the
 * pool carrying exactly — D5 no double-count). A position-REDUCING foreign line
 * creates NO lot (the consumption path is FIN-BE-025).
 *
 * <p>JPA annotations are the allowed domain↔framework exception (exactly like
 * {@link JournalLine} / {@link FxCostFlowConfig}). The DB CHECK constraints
 * (V10) mirror the factory's invariants ({@code original_foreign_minor > 0},
 * {@code remaining <= original}, non-negative bases).
 */
@Entity
@Table(name = "fx_position_lot")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FxPositionLot {

    @Id
    @Column(name = "lot_id", length = 36, nullable = false)
    private String lotId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    @Column(name = "seq", nullable = false)
    private long seq;

    @Column(name = "original_foreign_minor", nullable = false)
    private long originalForeignMinor;

    @Column(name = "original_base_minor", nullable = false)
    private long originalBaseMinor;

    @Column(name = "remaining_foreign_minor", nullable = false)
    private long remainingForeignMinor;

    @Column(name = "carrying_base_minor", nullable = false)
    private long carryingBaseMinor;

    @Column(name = "source_journal_entry_id", length = 36)
    private String sourceJournalEntryId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private FxPositionLot(String lotId, String tenantId, String ledgerAccountCode,
                          Currency currency, Instant acquiredAt, long seq,
                          long originalForeignMinor, long originalBaseMinor,
                          long remainingForeignMinor, long carryingBaseMinor,
                          String sourceJournalEntryId, Instant createdAt) {
        this.lotId = lotId;
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.currency = currency;
        this.acquiredAt = acquiredAt;
        this.seq = seq;
        this.originalForeignMinor = originalForeignMinor;
        this.originalBaseMinor = originalBaseMinor;
        this.remainingForeignMinor = remainingForeignMinor;
        this.carryingBaseMinor = carryingBaseMinor;
        this.sourceJournalEntryId = sourceJournalEntryId;
        this.createdAt = createdAt;
    }

    /**
     * Factory for a freshly-acquired lot (the acquisition hook). The lot is fully
     * open: {@code remaining_foreign_minor == original_foreign_minor} and
     * {@code carrying_base_minor == original_base_minor}. {@code seq} is the source
     * journal line's IDENTITY id (assigned after the entry is saved), so lots within
     * one position are FIFO-ordered by {@code (acquired_at, seq)}.
     *
     * @param foreignMinor strictly-positive foreign quantity acquired (a zero-amount
     *                     revaluation line is never a lot — guarded by the caller)
     * @param baseMinor    the non-negative base-currency (KRW) cost of the acquisition
     */
    public static FxPositionLot acquire(String tenantId, String ledgerAccountCode,
                                        Currency currency, Instant acquiredAt, long seq,
                                        long foreignMinor, long baseMinor,
                                        String sourceJournalEntryId, Instant createdAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (foreignMinor <= 0L) {
            throw new IllegalArgumentException(
                    "lot foreign quantity must be positive: " + foreignMinor);
        }
        if (baseMinor < 0L) {
            throw new IllegalArgumentException(
                    "lot base carrying must be non-negative: " + baseMinor);
        }
        return new FxPositionLot(java.util.UUID.randomUUID().toString(), tenantId,
                ledgerAccountCode, currency, acquiredAt, seq,
                foreignMinor, baseMinor, foreignMinor, baseMinor,
                sourceJournalEntryId, createdAt);
    }

    /**
     * Consume part (or all) of this open lot on a FIFO settlement (17th increment —
     * TASK-FIN-BE-025). Decrements the still-open {@code remaining_foreign_minor} by
     * {@code foreignMinor} and {@code carrying_base_minor} by {@code baseMinor} (the slice
     * of this lot's carrying realized by the settlement). The use case computes the slice
     * (HALF_UP) and walks lots {@code (acquired_at, seq)} ASC; when this lot is fully
     * consumed ({@code foreignMinor == remaining}) both decrements zero it out exactly.
     * Mutation + {@code save} is intentional here — settlement IS a state change of the
     * lot (unlike the immutable journal lines). Guards keep both balances non-negative
     * (the DB CHECK mirrors this); over-consumption is a programming error.
     *
     * @param foreignMinor the positive foreign quantity consumed from this lot
     *                     ({@code 0 < foreignMinor <= remaining_foreign_minor})
     * @param baseMinor    the non-negative carrying slice removed
     *                     ({@code 0 <= baseMinor <= carrying_base_minor})
     */
    public void consume(long foreignMinor, long baseMinor) {
        if (foreignMinor <= 0L) {
            throw new IllegalArgumentException(
                    "consumed foreign quantity must be positive: " + foreignMinor);
        }
        if (baseMinor < 0L) {
            throw new IllegalArgumentException(
                    "consumed carrying slice must be non-negative: " + baseMinor);
        }
        if (foreignMinor > remainingForeignMinor) {
            throw new IllegalArgumentException("over-consume: " + foreignMinor
                    + " > remaining " + remainingForeignMinor);
        }
        if (baseMinor > carryingBaseMinor) {
            throw new IllegalArgumentException("over-consume carrying: " + baseMinor
                    + " > carrying " + carryingBaseMinor);
        }
        this.remainingForeignMinor -= foreignMinor;
        this.carryingBaseMinor -= baseMinor;
    }
}
