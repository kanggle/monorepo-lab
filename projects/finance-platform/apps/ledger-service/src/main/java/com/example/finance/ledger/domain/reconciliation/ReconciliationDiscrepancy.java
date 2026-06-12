package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.error.LedgerErrors.ReconciliationAlreadyResolvedException;
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
import java.util.UUID;

/**
 * A recorded reconciliation mismatch (architecture.md § Reconciliation, fintech
 * F8). Mirrors the account-service {@code reconciliation_discrepancy} placeholder
 * columns ({@code expected_minor} / {@code actual_minor} / {@code status} /
 * {@code detected_at}). A discrepancy is created {@code OPEN} and transitions to
 * {@code RESOLVED} <b>only</b> via {@link #resolve} (the operator use case) —
 * there is NO auto-close / auto-adjust path anywhere (F8). The matcher only ever
 * RECORDS a discrepancy; it never posts a balancing entry.
 *
 * <p>Money is integer minor units ({@code expectedMinor} / {@code actualMinor})
 * + currency (F5 — never a float). JPA annotations are the allowed
 * domain↔framework exception; the discrepancy id is a {@code CHAR(36)} String
 * (the ledger id convention).
 */
@Entity
@Table(name = "reconciliation_discrepancy")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReconciliationDiscrepancy {

    @Id
    @Column(name = "discrepancy_id", length = 36, nullable = false)
    private String discrepancyId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    /** The statement this discrepancy was detected against (provenance; for the detail read). */
    @Column(name = "statement_id", length = 36)
    private String statementId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 20, nullable = false)
    private DiscrepancyType type;

    @Column(name = "external_ref", length = 128)
    private String externalRef;

    @Column(name = "journal_entry_id", length = 36)
    private String journalEntryId;

    @Column(name = "expected_minor", nullable = false)
    private long expectedMinor;

    @Column(name = "actual_minor", nullable = false)
    private long actualMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 10, nullable = false)
    private DiscrepancyStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "resolution_type", length = 20)
    private ResolutionType resolutionType;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    private ReconciliationDiscrepancy(String discrepancyId, String tenantId, String statementId,
                                      String ledgerAccountCode, DiscrepancyType type,
                                      String externalRef, String journalEntryId,
                                      long expectedMinor, long actualMinor, Currency currency,
                                      Instant detectedAt) {
        this.discrepancyId = discrepancyId;
        this.tenantId = tenantId;
        this.statementId = statementId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.type = type;
        this.externalRef = externalRef;
        this.journalEntryId = journalEntryId;
        this.expectedMinor = expectedMinor;
        this.actualMinor = actualMinor;
        this.currency = currency;
        this.status = DiscrepancyStatus.OPEN;
        this.detectedAt = detectedAt;
    }

    /**
     * Record a NEW discrepancy — always {@code OPEN} (F8: a detected mismatch is
     * surfaced for operator review, never auto-closed). The id is generated when
     * absent.
     */
    public static ReconciliationDiscrepancy open(String discrepancyId, String tenantId,
                                                 String statementId, String ledgerAccountCode,
                                                 DiscrepancyType type, String externalRef,
                                                 String journalEntryId, long expectedMinor,
                                                 long actualMinor, Currency currency,
                                                 Instant detectedAt) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ledgerAccountCode, "ledgerAccountCode");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(detectedAt, "detectedAt");
        String id = discrepancyId != null ? discrepancyId : UUID.randomUUID().toString();
        return new ReconciliationDiscrepancy(id, tenantId, statementId, ledgerAccountCode, type,
                externalRef, journalEntryId, expectedMinor, actualMinor, currency, detectedAt);
    }

    /**
     * Operator resolution — the ONLY mutator on this aggregate (F8: resolution is
     * operator-only, never automatic). Requires {@code status == OPEN} else
     * {@link ReconciliationAlreadyResolvedException}; flips OPEN→RESOLVED and
     * stamps the resolution record (type / note / resolvedBy / resolvedAt). It
     * never posts a balancing entry or adjusts the recorded amounts.
     */
    public void resolve(ResolutionType resolutionType, String note, String resolvedBy,
                        Instant resolvedAt) {
        if (status != DiscrepancyStatus.OPEN) {
            throw new ReconciliationAlreadyResolvedException(
                    "reconciliation discrepancy already resolved: " + discrepancyId);
        }
        Objects.requireNonNull(resolutionType, "resolutionType");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolutionType = resolutionType;
        this.note = note;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = resolvedAt;
    }

    public boolean isOpen() {
        return status == DiscrepancyStatus.OPEN;
    }
}
