package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * External-statement aggregate root (architecture.md § Reconciliation). A batch
 * of external settlement {@link ExternalStatementLine}s for ONE reconciled
 * clearing account ({@code CASH_CLEARING} / {@code SETTLEMENT_SUSPENSE}), from a
 * {@link StatementSource} (bank / PG / other), for a statement date.
 *
 * <p>Ingested once and immutable thereafter (only its lines' {@code matchStatus}
 * flips during matching — F3 parity). JPA annotations are the allowed
 * domain↔framework exception; the statement id is a {@code CHAR(36)} String (the
 * ledger id convention — never the outbox UUID type).
 */
@Entity
@Table(name = "reconciliation_statement")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalStatement {

    @Id
    @Column(name = "statement_id", length = 36, nullable = false)
    private String statementId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source", length = 10, nullable = false)
    private StatementSource source;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "statement_id", referencedColumnName = "statement_id",
            insertable = false, updatable = false)
    private List<ExternalStatementLine> lines = new ArrayList<>();

    private ExternalStatement(String statementId, String tenantId, String ledgerAccountCode,
                              StatementSource source, LocalDate statementDate,
                              Instant ingestedAt, List<ExternalStatementLine> lines) {
        this.statementId = statementId;
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.source = source;
        this.statementDate = statementDate;
        this.ingestedAt = ingestedAt;
        this.lines = lines;
    }

    /**
     * Open a new statement from raw line inputs. Each {@code RawLine} becomes an
     * {@link ExternalStatementLine} carrying this statement's id + tenant. The id
     * is generated when absent.
     */
    public static ExternalStatement open(String statementId, String tenantId,
                                         String ledgerAccountCode, StatementSource source,
                                         LocalDate statementDate, Instant ingestedAt,
                                         List<RawLine> rawLines) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(statementDate, "statementDate");
        Objects.requireNonNull(ingestedAt, "ingestedAt");
        Objects.requireNonNull(rawLines, "rawLines");
        String id = statementId != null ? statementId : UUID.randomUUID().toString();
        List<ExternalStatementLine> lines = new ArrayList<>(rawLines.size());
        for (RawLine raw : rawLines) {
            lines.add(ExternalStatementLine.of(null, id, tenantId, raw.externalRef(),
                    raw.money(), raw.direction(), raw.valueDate(), raw.description(),
                    raw.baseAmount()));
        }
        return new ExternalStatement(id, tenantId, ledgerAccountCode, source,
                statementDate, ingestedAt, lines);
    }

    /** Defensive copy — the line list is owned by the aggregate. */
    public List<ExternalStatementLine> lines() {
        return Collections.unmodifiableList(lines);
    }

    /**
     * A raw ingest line (the use case's parsed request input). (11th incr —
     * TASK-FIN-BE-017) the optional {@code baseAmount} is the bank-reported base/KRW
     * value for a foreign-currency line ({@code null} for a KRW / base-less line).
     */
    public record RawLine(String externalRef, Money money, EntryDirection direction,
                          LocalDate valueDate, String description, Money baseAmount) {
    }
}
