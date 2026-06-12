package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerEntryUnbalancedException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Journal entry aggregate root (architecture.md § Layer Structure / § Posting
 * Policy / § Immutability). A balanced set of {@link JournalLine}s — the core
 * invariant {@code Σ debit == Σ credit} is <b>self-validated in the factory</b>
 * ({@link #post}) so it is structurally impossible to construct (and therefore
 * persist) an unbalanced entry (F2). Once posted the entry is immutable — there
 * is no mutator; a correction is a NEW {@code REVERSAL} entry built from
 * {@link #reversalEntry} (F3).
 *
 * <p>Single-currency per entry: all lines must share one {@link Currency} or the
 * factory raises {@link CurrencyMismatchException} (422 CURRENCY_MISMATCH).
 *
 * <p>JPA annotations are the allowed domain↔framework exception; the balance
 * identity is pure Java and exhaustively unit-tested.
 */
@Entity
@Table(name = "journal_entry")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalEntry {

    @Id
    @Column(name = "entry_id", length = 36, nullable = false)
    private String entryId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Embedded
    private SourceRef source;

    @Column(name = "reversal_of_entry_id", length = 36)
    private String reversalOfEntryId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @JoinColumn(name = "entry_id", referencedColumnName = "entry_id",
            insertable = false, updatable = false)
    private List<JournalLine> lines = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private JournalEntry(String entryId, String tenantId, Instant postedAt,
                         SourceRef source, String reversalOfEntryId,
                         List<JournalLine> lines) {
        this.entryId = entryId;
        this.tenantId = tenantId;
        this.postedAt = postedAt;
        this.source = source;
        this.reversalOfEntryId = reversalOfEntryId;
        this.lines = lines;
    }

    /**
     * Construct a posted journal entry, re-asserting the balance identity. The
     * lines MUST be ≥2, single-currency, and balanced ({@code Σ debit == Σ
     * credit}) — otherwise {@link LedgerEntryUnbalancedException} /
     * {@link CurrencyMismatchException}.
     */
    public static JournalEntry post(String entryId, String tenantId, Instant postedAt,
                                    SourceRef source, List<JournalLine> lines) {
        return create(entryId, tenantId, postedAt, source, null, lines);
    }

    /**
     * Build a compensating REVERSAL entry from an original entry: every line's
     * debit/credit is swapped, {@code reversalOfEntryId} references the original
     * (F3). The swapped set is balanced by construction; the factory re-asserts.
     */
    public static JournalEntry reversalEntry(String entryId, Instant postedAt,
                                             SourceRef source, JournalEntry original) {
        Objects.requireNonNull(original, "original");
        List<JournalLine> swapped = new ArrayList<>(original.lines.size());
        for (JournalLine line : original.lines) {
            swapped.add(line.reversed());
        }
        return create(entryId, original.tenantId, postedAt, source,
                original.entryId, swapped);
    }

    private static JournalEntry create(String entryId, String tenantId, Instant postedAt,
                                       SourceRef source, String reversalOfEntryId,
                                       List<JournalLine> lines) {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(postedAt, "postedAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(lines, "lines");
        if (lines.size() < 2) {
            throw new LedgerEntryUnbalancedException(
                    "a journal entry must have at least two lines, got " + lines.size());
        }
        Currency currency = lines.get(0).currency();
        Money debitTotal = Money.zero(currency);
        Money creditTotal = Money.zero(currency);
        for (JournalLine line : lines) {
            if (line.currency() != currency) {
                throw new CurrencyMismatchException(
                        "cross-currency lines in one entry: " + currency
                                + " vs " + line.currency());
            }
            if (line.isDebit()) {
                debitTotal = debitTotal.add(line.money());
            } else {
                creditTotal = creditTotal.add(line.money());
            }
        }
        if (!debitTotal.equals(creditTotal)) {
            throw new LedgerEntryUnbalancedException(
                    "unbalanced entry: Σ debit " + debitTotal
                            + " != Σ credit " + creditTotal);
        }
        List<JournalLine> attached = new ArrayList<>(lines);
        for (JournalLine line : attached) {
            line.attachTo(entryId, postedAt);
        }
        return new JournalEntry(entryId, tenantId, postedAt, source,
                reversalOfEntryId, attached);
    }

    /** Defensive copy — the line list is immutable to callers (F3). */
    public List<JournalLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    public Currency currency() {
        return lines.get(0).currency();
    }

    public Money debitTotal() {
        Money total = Money.zero(currency());
        for (JournalLine line : lines) {
            if (line.isDebit()) {
                total = total.add(line.money());
            }
        }
        return total;
    }

    public Money creditTotal() {
        Money total = Money.zero(currency());
        for (JournalLine line : lines) {
            if (line.isCredit()) {
                total = total.add(line.money());
            }
        }
        return total;
    }

    /** Always true for a constructed entry (the factory rejects unbalanced sets). */
    public boolean isBalanced() {
        return debitTotal().equals(creditTotal());
    }

    public boolean isReversal() {
        return reversalOfEntryId != null;
    }
}
