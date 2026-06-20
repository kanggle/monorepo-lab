package com.example.settlement.domain.period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * Settlement-period aggregate root (architecture.md § Period close). Closes a
 * half-open {@code [period_from, period_to)} window: {@code covers(t) ⇔ from ≤ t <
 * to} (consecutive windows abut at the boundary with no gap, no overlap). The state
 * machine is {@code OPEN → CLOSED} (no reopen — forward-declared). Mirrors the
 * finance-platform ledger {@code AccountingPeriod}.
 *
 * <p>This is the ONE mutating aggregate in settlement (the commission-accrual ledger
 * stays immutable, F3): {@link #close} flips OPEN→CLOSED and stamps the close-time
 * fields. The {@code covers} / {@code overlaps} predicates are pure Java and
 * exhaustively unit-tested. JPA annotations are the allowed domain↔framework
 * exception (exactly like {@code CommissionAccrualJpaEntity}).
 */
@Entity
@Table(name = "settlement_period")
@Getter
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementPeriod {

    @Id
    @Column(name = "period_id", length = 255, nullable = false)
    private String periodId;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "period_from", nullable = false)
    private Instant from;

    @Column(name = "period_to", nullable = false)
    private Instant to;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PeriodStatus status;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 255)
    private String closedBy;

    @Column(name = "seller_count")
    private Integer sellerCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private SettlementPeriod(String periodId, String tenantId, Instant from, Instant to,
                             PeriodStatus status) {
        this.periodId = periodId;
        this.tenantId = tenantId;
        this.from = from;
        this.to = to;
        this.status = status;
    }

    /**
     * Open a new period over a half-open {@code [from, to)} window. The window must
     * be non-empty ({@code from < to}) or {@link PeriodWindowInvalidException}.
     */
    public static SettlementPeriod open(String periodId, String tenantId,
                                        Instant from, Instant to) {
        Objects.requireNonNull(periodId, "periodId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!from.isBefore(to)) {
            throw new PeriodWindowInvalidException(
                    "settlement period window must be non-empty (from < to): from=" + from
                            + " to=" + to);
        }
        return new SettlementPeriod(periodId, tenantId, from, to, PeriodStatus.OPEN);
    }

    /**
     * Close this period (OPEN→CLOSED), stamping the close-time record. A second close
     * → {@link PeriodAlreadyClosedException}. This is the single allowed aggregate
     * mutation (accruals remain immutable, F3). {@code sellerCount} is the number of
     * sellers with a positive payable (the count of {@code seller_payout} rows
     * created — net-zero sellers are skipped, decision 7).
     */
    public void close(Instant closedAt, String closedBy, int sellerCount) {
        if (status != PeriodStatus.OPEN) {
            throw new PeriodAlreadyClosedException(
                    "settlement period already closed: " + periodId);
        }
        Objects.requireNonNull(closedAt, "closedAt");
        this.status = PeriodStatus.CLOSED;
        this.closedAt = closedAt;
        this.closedBy = closedBy;
        this.sellerCount = sellerCount;
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
