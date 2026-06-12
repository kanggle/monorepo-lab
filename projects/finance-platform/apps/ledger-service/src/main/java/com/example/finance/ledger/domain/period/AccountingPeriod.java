package com.example.finance.ledger.domain.period;

import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodAlreadyClosedException;
import com.example.finance.ledger.domain.error.LedgerErrors.AccountingPeriodInvalidWindowException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Accounting period aggregate root (architecture.md § Accounting Period). Locks
 * the books for a half-open {@code [from, to)} window: {@code covers(t) ⇔ from ≤ t
 * < to} (consecutive periods abut at the boundary with no gap, no overlap). The
 * state machine is {@code OPEN → CLOSED} (no reopen — forward-declared).
 *
 * <p>This is the ONE allowed mutating aggregate in the ledger (journal entries
 * stay immutable, F3): {@link #close} flips OPEN→CLOSED and stamps the close-time
 * fields. The {@code covers} / {@code overlaps} predicates are pure Java and
 * exhaustively unit-tested. JPA annotations are the allowed domain↔framework
 * exception (exactly like {@code JournalEntry}).
 */
@Entity
@Table(name = "accounting_period")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountingPeriod {

    @Id
    @Column(name = "period_id", length = 36, nullable = false)
    private String periodId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "period_from", nullable = false)
    private Instant from;

    @Column(name = "period_to", nullable = false)
    private Instant to;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 10, nullable = false)
    private PeriodStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 128)
    private String closedBy;

    @Column(name = "entry_count")
    private Long entryCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private AccountingPeriod(String periodId, String tenantId, Instant from, Instant to,
                             PeriodStatus status) {
        this.periodId = periodId;
        this.tenantId = tenantId;
        this.from = from;
        this.to = to;
        this.status = status;
    }

    /**
     * Open a new period over a half-open {@code [from, to)} window. The window
     * must be non-empty ({@code from < to}) or {@link AccountingPeriodInvalidWindowException}.
     */
    public static AccountingPeriod open(String periodId, String tenantId,
                                        Instant from, Instant to) {
        Objects.requireNonNull(periodId, "periodId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new AccountingPeriodInvalidWindowException(
                    "accounting period window must be non-empty (from < to): from=" + from
                            + " to=" + to);
        }
        return new AccountingPeriod(periodId, tenantId, from, to, PeriodStatus.OPEN);
    }

    /**
     * Close this period (OPEN→CLOSED), stamping the close-time record. A second
     * close → {@link AccountingPeriodAlreadyClosedException}. This is the single
     * allowed aggregate mutation (entries remain immutable, F3).
     */
    public void close(Instant closedAt, String closedBy, long entryCount) {
        if (status != PeriodStatus.OPEN) {
            throw new AccountingPeriodAlreadyClosedException(
                    "accounting period already closed: " + periodId);
        }
        Objects.requireNonNull(closedAt, "closedAt");
        this.status = PeriodStatus.CLOSED;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.entryCount = entryCount;
    }

    public boolean isClosed() {
        return status == PeriodStatus.CLOSED;
    }

    /** Half-open coverage: {@code from ≤ t < to} (from inclusive, to exclusive). */
    public boolean covers(Instant t) {
        Objects.requireNonNull(t, "t");
        return !t.isBefore(from) && t.isBefore(to);
    }

    /**
     * True iff this period's window overlaps {@code [otherFrom, otherTo)}. Two
     * half-open windows overlap iff each starts before the other ends — abutting
     * windows (one's {@code to} equals the other's {@code from}) do NOT overlap.
     */
    public boolean overlaps(Instant otherFrom, Instant otherTo) {
        Objects.requireNonNull(otherFrom, "otherFrom");
        Objects.requireNonNull(otherTo, "otherTo");
        return from.isBefore(otherTo) && otherFrom.isBefore(to);
    }
}
