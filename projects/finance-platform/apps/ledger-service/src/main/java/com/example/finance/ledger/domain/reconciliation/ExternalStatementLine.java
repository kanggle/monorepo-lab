package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.error.LedgerErrors.CurrencyMismatchException;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import com.example.finance.ledger.domain.money.Money;
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

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One settlement line of an {@link ExternalStatement} (architecture.md
 * § Reconciliation). Carries the external reference, the {@link Money} amount,
 * the {@link EntryDirection} relative to the reconciled clearing account, the
 * value date, an optional description, and a {@link LineMatchStatus}
 * ({@code UNMATCHED} on ingest, flipped to {@code MATCHED} by the matcher).
 *
 * <p>Money is integer minor units only (F5). JPA annotations are the allowed
 * domain↔framework exception; the line's id is a {@code CHAR(36)} String (the
 * ledger id convention — never the outbox UUID type).
 *
 * <p><b>(11th incr — TASK-FIN-BE-017, multi-currency reconciliation)</b> the line
 * gains an <b>optional</b> base value — {@code baseAmountMinor} (nullable) +
 * {@code baseCurrency} (nullable) — the bank's reported value in the base/reporting
 * currency (KRW) for a foreign-currency line, at the bank's FX rate. It is
 * {@code NULL} when the statement does not carry one (a KRW line, or a foreign line
 * without a declared base — net-zero). When present it MUST be the base currency
 * (KRW); {@link #baseAmount()} returns {@code null} when absent.
 */
@Entity
@Table(name = "reconciliation_statement_line")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalStatementLine {

    @Id
    @Column(name = "line_id", length = 36, nullable = false)
    private String lineId;

    @Column(name = "statement_id", length = 36, nullable = false)
    private String statementId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "external_ref", length = 128, nullable = false)
    private String externalRef;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "direction", length = 10, nullable = false)
    private EntryDirection direction;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "description", length = 256)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "match_status", length = 10, nullable = false)
    private LineMatchStatus matchStatus;

    // (11th incr) optional base/reporting-currency (KRW) value reported by the bank
    // for a foreign-currency line, at the bank's FX rate. NULL when absent (a KRW
    // line, or a foreign line without a declared base — net-zero).
    @Column(name = "base_amount_minor")
    private Long baseAmountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "base_currency", length = 3)
    private Currency baseCurrency;

    private ExternalStatementLine(String lineId, String statementId, String tenantId,
                                  String externalRef, Money money, EntryDirection direction,
                                  LocalDate valueDate, String description, Money baseAmount) {
        this.lineId = lineId;
        this.statementId = statementId;
        this.tenantId = tenantId;
        this.externalRef = externalRef;
        this.amountMinor = money.minorUnits();
        this.currency = money.currency();
        this.direction = direction;
        this.valueDate = valueDate;
        this.description = description;
        this.matchStatus = LineMatchStatus.UNMATCHED;
        // (11th incr) base fields are null when no base amount is declared.
        this.baseAmountMinor = baseAmount == null ? null : baseAmount.minorUnits();
        this.baseCurrency = baseAmount == null ? null : baseAmount.currency();
    }

    /** A new, UNMATCHED statement line with no base amount (KRW / base-less). */
    public static ExternalStatementLine of(String lineId, String statementId, String tenantId,
                                           String externalRef, Money money,
                                           EntryDirection direction, LocalDate valueDate,
                                           String description) {
        return of(lineId, statementId, tenantId, externalRef, money, direction, valueDate,
                description, null);
    }

    /**
     * A new, UNMATCHED statement line with an <b>optional</b> base amount (11th incr).
     * When {@code baseAmount} is non-null it MUST be the base/reporting currency (KRW
     * in v1) — else {@link CurrencyMismatchException} (mirrors
     * {@code JournalLine.of(money, baseAmount)}). The line id is generated when absent.
     */
    public static ExternalStatementLine of(String lineId, String statementId, String tenantId,
                                           String externalRef, Money money,
                                           EntryDirection direction, LocalDate valueDate,
                                           String description, Money baseAmount) {
        Objects.requireNonNull(statementId, "statementId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(externalRef, "externalRef");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(valueDate, "valueDate");
        if (baseAmount != null && baseAmount.currency() != LedgerReportingCurrency.BASE) {
            throw new CurrencyMismatchException(
                    "statement-line base amount must be the reporting currency "
                            + LedgerReportingCurrency.BASE + ", got " + baseAmount.currency());
        }
        String id = lineId != null ? lineId : UUID.randomUUID().toString();
        return new ExternalStatementLine(id, statementId, tenantId, externalRef, money,
                direction, valueDate, description, baseAmount);
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }

    /**
     * (11th incr) the bank-reported base/reporting-currency (KRW) value for this line,
     * or {@code null} when none was declared (the base-leg check never fires).
     */
    public Money baseAmount() {
        return baseAmountMinor == null ? null : Money.of(baseAmountMinor, baseCurrency);
    }

    public boolean isMatched() {
        return matchStatus == LineMatchStatus.MATCHED;
    }

    /** Flip this line to {@code MATCHED} once the matcher pairs it with an internal line. */
    public void markMatched() {
        this.matchStatus = LineMatchStatus.MATCHED;
    }
}
