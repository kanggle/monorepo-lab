package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.money.Currency;
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

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorded 1:1 link between a matched {@link ExternalStatementLine} and an
 * internal {@code journalEntryId} on the reconciled clearing account
 * (architecture.md § Reconciliation). Insert-only — a match is the immutable
 * record of one successful pairing (F3 parity). Money is integer minor units (F5).
 *
 * <p>JPA annotations are the allowed domain↔framework exception; the match id is
 * a {@code CHAR(36)} String (the ledger id convention).
 *
 * <p><b>(14th incr — TASK-FIN-BE-021, cross-currency base-leg matching)</b> the match
 * gains a {@code crossCurrency} audit flag — {@code true} iff this is a base-currency
 * (KRW) external line matched to a <b>foreign</b> internal line by its carrying base
 * (a regulated-ledger audit-transparency marker — "this KRW bank line matched a foreign
 * ledger position by carrying base"). Every same-currency match sets it {@code false}
 * (the additive {@code V8} column defaults {@code FALSE} — net-zero for existing rows).
 */
@Entity
@Table(name = "reconciliation_match")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationMatch {

    @Id
    @Column(name = "match_id", length = 36, nullable = false)
    private String matchId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "statement_line_id", length = 36, nullable = false)
    private String statementLineId;

    @Column(name = "external_ref", length = 128, nullable = false)
    private String externalRef;

    @Column(name = "journal_entry_id", length = 36, nullable = false)
    private String journalEntryId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "matched_at", nullable = false)
    private Instant matchedAt;

    // (14th incr — TASK-FIN-BE-021) audit flag: true iff a base-currency (KRW) external
    // line matched a FOREIGN internal line by its carrying base. Same-currency = false
    // (the additive V8 column defaults FALSE — net-zero for existing rows).
    @Column(name = "cross_currency", nullable = false)
    private boolean crossCurrency;

    private ReconciliationMatch(String matchId, String tenantId, String statementLineId,
                                String externalRef, String journalEntryId,
                                String ledgerAccountCode, Money money, boolean crossCurrency,
                                Instant matchedAt) {
        this.matchId = matchId;
        this.tenantId = tenantId;
        this.statementLineId = statementLineId;
        this.externalRef = externalRef;
        this.journalEntryId = journalEntryId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.amountMinor = money.minorUnits();
        this.currency = money.currency();
        this.crossCurrency = crossCurrency;
        this.matchedAt = matchedAt;
    }

    /** A same-currency match (the dominant path) — {@code crossCurrency = false}. */
    public static ReconciliationMatch of(String matchId, String tenantId, String statementLineId,
                                         String externalRef, String journalEntryId,
                                         String ledgerAccountCode, Money money, Instant matchedAt) {
        return of(matchId, tenantId, statementLineId, externalRef, journalEntryId,
                ledgerAccountCode, money, false, matchedAt);
    }

    /**
     * (14th incr — TASK-FIN-BE-021) A match with an explicit {@code crossCurrency} flag
     * ({@code true} for a base-currency external matched to a foreign internal by its
     * carrying base; {@code false} for every same-currency match).
     */
    public static ReconciliationMatch of(String matchId, String tenantId, String statementLineId,
                                         String externalRef, String journalEntryId,
                                         String ledgerAccountCode, Money money,
                                         boolean crossCurrency, Instant matchedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(statementLineId, "statementLineId");
        Objects.requireNonNull(externalRef, "externalRef");
        Objects.requireNonNull(journalEntryId, "journalEntryId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(matchedAt, "matchedAt");
        String id = matchId != null ? matchId : UUID.randomUUID().toString();
        return new ReconciliationMatch(id, tenantId, statementLineId, externalRef,
                journalEntryId, ledgerAccountCode, money, crossCurrency, matchedAt);
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }
}
