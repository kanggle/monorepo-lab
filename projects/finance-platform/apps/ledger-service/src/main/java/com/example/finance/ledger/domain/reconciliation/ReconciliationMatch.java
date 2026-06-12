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

    private ReconciliationMatch(String matchId, String tenantId, String statementLineId,
                                String externalRef, String journalEntryId,
                                String ledgerAccountCode, Money money, Instant matchedAt) {
        this.matchId = matchId;
        this.tenantId = tenantId;
        this.statementLineId = statementLineId;
        this.externalRef = externalRef;
        this.journalEntryId = journalEntryId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.amountMinor = money.minorUnits();
        this.currency = money.currency();
        this.matchedAt = matchedAt;
    }

    public static ReconciliationMatch of(String matchId, String tenantId, String statementLineId,
                                         String externalRef, String journalEntryId,
                                         String ledgerAccountCode, Money money, Instant matchedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(statementLineId, "statementLineId");
        Objects.requireNonNull(externalRef, "externalRef");
        Objects.requireNonNull(journalEntryId, "journalEntryId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(matchedAt, "matchedAt");
        String id = matchId != null ? matchId : UUID.randomUUID().toString();
        return new ReconciliationMatch(id, tenantId, statementLineId, externalRef,
                journalEntryId, ledgerAccountCode, money, matchedAt);
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }
}
