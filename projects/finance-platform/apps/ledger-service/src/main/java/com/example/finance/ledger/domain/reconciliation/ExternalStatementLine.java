package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.journal.EntryDirection;
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

    private ExternalStatementLine(String lineId, String statementId, String tenantId,
                                  String externalRef, Money money, EntryDirection direction,
                                  LocalDate valueDate, String description) {
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
    }

    /** A new, UNMATCHED statement line. The line id is generated when absent. */
    public static ExternalStatementLine of(String lineId, String statementId, String tenantId,
                                           String externalRef, Money money,
                                           EntryDirection direction, LocalDate valueDate,
                                           String description) {
        Objects.requireNonNull(statementId, "statementId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(externalRef, "externalRef");
        Objects.requireNonNull(money, "money");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(valueDate, "valueDate");
        String id = lineId != null ? lineId : UUID.randomUUID().toString();
        return new ExternalStatementLine(id, statementId, tenantId, externalRef, money,
                direction, valueDate, description);
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }

    public boolean isMatched() {
        return matchStatus == LineMatchStatus.MATCHED;
    }

    /** Flip this line to {@code MATCHED} once the matcher pairs it with an internal line. */
    public void markMatched() {
        this.matchStatus = LineMatchStatus.MATCHED;
    }
}
