package com.example.finance.ledger.domain.journal;

import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * One debit-or-credit line of a {@link JournalEntry} (architecture.md § Layer
 * Structure). Immutable once posted — there is no business setter and the owning
 * entry never updates a line (F3). Money is integer minor units only (F5).
 *
 * <p>The {@code entry_id} + {@code posted_at} columns are denormalized onto the
 * line so the per-account lines view and the trial-balance totals are simple
 * tenant-scoped line queries (no entry join). They are stamped by the owning
 * entry at post time ({@link JournalEntry#post}). JPA annotations are the allowed
 * domain↔framework exception; the line's semantics are pure.
 *
 * <p><b>(8th increment — multi-currency, TASK-FIN-BE-014)</b> the line keeps its
 * transaction {@link Money} ({@code amountMinor} + {@code currency}) and gains its
 * value in the fixed base/reporting currency ({@link LedgerReportingCurrency#BASE}
 * = KRW): {@code baseAmountMinor} (a {@code long}, <b>authoritative for the
 * entry's balance check</b>) + {@code baseCurrency} + {@code exchangeRate} (an
 * exact {@link BigDecimal} = {@code baseAmount.minor / money.minor}, recorded for
 * provenance and <b>never</b> used to re-derive the balance — F5 preserved: only
 * the rate is a decimal, money stays integer). A base-currency (KRW) line has
 * {@code baseAmount == money}, {@code exchangeRate == 1}.
 */
@Entity
@Table(name = "journal_line")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entry_id", length = 36, nullable = false)
    private String entryId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", length = 10, nullable = false)
    private EntryDirection direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    // (8th incr) per-line FX provenance + base-currency value (KRW). exchangeRate
    // is a decimal recorded for provenance; baseAmountMinor is authoritative for
    // the entry balance check. DECIMAL(20,8) maps to BigDecimal natively.
    @Column(name = "exchange_rate", precision = 20, scale = 8, nullable = false)
    private BigDecimal exchangeRate;

    @Column(name = "base_amount_minor", nullable = false)
    private long baseAmountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "base_currency", length = 3, nullable = false)
    private Currency baseCurrency;

    private JournalLine(String tenantId, String ledgerAccountCode,
                        EntryDirection direction, Money money,
                        Money baseAmount, BigDecimal exchangeRate) {
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.direction = direction;
        this.amountMinor = money.minorUnits();
        this.currency = money.currency();
        this.baseAmountMinor = baseAmount.minorUnits();
        this.baseCurrency = baseAmount.currency();
        this.exchangeRate = exchangeRate;
    }

    /**
     * Single-currency convenience form (auto-journal + base-currency manual lines).
     * The base amount IS the transaction money ({@code baseAmount == money},
     * {@code exchangeRate == 1}) — net-zero with the pre-8th-increment behaviour.
     * The auto-journal {@code PostingPolicy} only ever passes KRW money here, so
     * the line balances against KRW base sums.
     */
    public static JournalLine of(String tenantId, String ledgerAccountCode,
                                 EntryDirection direction, Money money) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(money, "money");
        if (!money.isPositive()) {
            throw new IllegalArgumentException(
                    "journal line amount must be positive: " + money);
        }
        // base = money (same currency), rate = 1. The single-arg form's base is in
        // money's own currency; the entry factory only sums base amounts that are
        // all the base currency, so a non-base single-arg line is rejected there as
        // unbalanced (which is correct — a foreign line must supply its base amount).
        return new JournalLine(tenantId, ledgerAccountCode, direction, money,
                money, BigDecimal.ONE);
    }

    /**
     * Multi-currency form (8th increment): a line whose transaction {@code money}
     * is in any supported currency, carrying its explicit value in the base
     * currency ({@code baseAmount}, KRW — authoritative for the balance). The
     * {@code exchangeRate} is derived as the minor-to-minor provenance factor
     * {@code baseAmount.minor / money.minor} (exact {@link BigDecimal}, scale 8);
     * a zero-amount line keeps rate = 1.
     *
     * @throws CurrencyMismatchException if {@code baseAmount} is not the base
     *         currency (KRW in v1)
     */
    public static JournalLine of(String tenantId, String ledgerAccountCode,
                                 EntryDirection direction, Money money, Money baseAmount) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(baseAmount, "baseAmount");
        if (!money.isPositive()) {
            throw new IllegalArgumentException(
                    "journal line amount must be positive: " + money);
        }
        if (baseAmount.currency() != LedgerReportingCurrency.BASE) {
            throw new CurrencyMismatchException(
                    "base amount must be the reporting currency "
                            + LedgerReportingCurrency.BASE + ", got " + baseAmount.currency());
        }
        return new JournalLine(tenantId, ledgerAccountCode, direction, money,
                baseAmount, rateOf(money, baseAmount));
    }

    /** The exact minor-to-minor provenance factor (never re-derives the balance). */
    private static BigDecimal rateOf(Money money, Money baseAmount) {
        if (money.minorUnits() == 0L) {
            return BigDecimal.ONE;
        }
        return new BigDecimal(baseAmount.minorUnits())
                .divide(new BigDecimal(money.minorUnits()), 8, RoundingMode.HALF_UP);
    }

    public static JournalLine debit(String tenantId, String code, Money money) {
        return of(tenantId, code, EntryDirection.DEBIT, money);
    }

    public static JournalLine credit(String tenantId, String code, Money money) {
        return of(tenantId, code, EntryDirection.CREDIT, money);
    }

    /** Stamp the owning entry's id + posting instant (called by the entry factory). */
    void attachTo(String entryId, Instant postedAt) {
        this.entryId = entryId;
        this.postedAt = postedAt;
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }

    /** The line's value in the base/reporting currency (KRW) — balance-authoritative. */
    public Money baseMoney() {
        return Money.of(baseAmountMinor, baseCurrency);
    }

    public boolean isDebit() {
        return direction == EntryDirection.DEBIT;
    }

    public boolean isCredit() {
        return direction == EntryDirection.CREDIT;
    }

    /**
     * A line on the opposite side with the same account + money (reversal, F3).
     * (8th incr) preserves the transaction money, the {@code exchangeRate}, and the
     * {@code baseAmount} (only the direction flips), so the reversal balances in the
     * base currency by construction.
     */
    public JournalLine reversed() {
        return new JournalLine(tenantId, ledgerAccountCode, direction.opposite(),
                money(), baseMoney(), exchangeRate);
    }
}
