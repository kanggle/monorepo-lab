package com.example.finance.account.domain.balance;

import com.example.finance.account.domain.error.DomainErrors.AmountInvalidException;
import com.example.finance.account.domain.error.DomainErrors.HoldAlreadySettledException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
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

import java.time.Instant;
import java.util.Objects;

/**
 * A fund hold against an account's available balance (architecture.md §
 * Balance Model). Lifecycle is driven only through {@link #capture} /
 * {@link #release} / {@link #expire}; a settled hold rejects re-settlement
 * (F: HOLD_ALREADY_SETTLED). Money is integer minor units only (F5).
 *
 * <p>JPA annotations are the allowed domain↔framework exception; the
 * money math is pure.
 */
@Entity
@Table(name = "holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hold {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    /** Minor units captured so far (≤ amountMinor). Remainder is released. */
    @Column(name = "captured_minor", nullable = false)
    private long capturedMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private HoldStatus status;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Hold place(String id,
                             String accountId,
                             String tenantId,
                             Money amount,
                             String reason,
                             Instant now,
                             Instant expiresAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!amount.isPositive()) {
            throw new AmountInvalidException("hold amount must be > 0");
        }
        Hold h = new Hold();
        h.id = id;
        h.accountId = accountId;
        h.tenantId = tenantId;
        h.amountMinor = amount.minorUnits();
        h.currency = amount.currency();
        h.capturedMinor = 0L;
        h.status = HoldStatus.ACTIVE;
        h.reason = reason;
        h.createdAt = now;
        h.expiresAt = expiresAt;
        return h;
    }

    public Money amount() {
        return Money.of(amountMinor, currency);
    }

    public Money capturedAmount() {
        return Money.of(capturedMinor, currency);
    }

    private void ensureActive() {
        if (status.isSettled()) {
            throw new HoldAlreadySettledException(
                    "Hold " + id + " already " + status);
        }
    }

    /**
     * Capture (full or partial). {@code captureAmount ≤ hold amount} and same
     * currency. Returns the auto-released remainder (hold amount − captured).
     */
    public Money capture(Money captureAmount, Instant now) {
        ensureActive();
        if (captureAmount.currency() != this.currency) {
            throw new com.example.finance.account.domain.error.DomainErrors
                    .CurrencyMismatchException(
                    "capture currency " + captureAmount.currency()
                            + " != hold currency " + this.currency);
        }
        if (!captureAmount.isPositive()) {
            throw new AmountInvalidException("capture amount must be > 0");
        }
        if (captureAmount.minorUnits() > this.amountMinor) {
            throw new AmountInvalidException(
                    "capture amount " + captureAmount.minorUnits()
                            + " exceeds hold amount " + this.amountMinor);
        }
        this.capturedMinor = captureAmount.minorUnits();
        this.status = HoldStatus.CAPTURED;
        this.settledAt = now;
        return Money.of(this.amountMinor - this.capturedMinor, this.currency);
    }

    /** Release the full hold (no capture). Funds return to available. */
    public Money release(Instant now) {
        ensureActive();
        this.status = HoldStatus.RELEASED;
        this.settledAt = now;
        return amount();
    }

    /** Auto-release on expiry (sweep). Same effect as a release. */
    public Money expire(Instant now) {
        ensureActive();
        this.status = HoldStatus.EXPIRED;
        this.settledAt = now;
        return amount();
    }

    public boolean isActive() {
        return this.status == HoldStatus.ACTIVE;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(this.expiresAt);
    }
}
