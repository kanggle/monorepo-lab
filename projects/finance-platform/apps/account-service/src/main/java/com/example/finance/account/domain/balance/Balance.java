package com.example.finance.account.domain.balance;

import com.example.finance.account.domain.error.DomainErrors.InsufficientAvailableBalanceException;
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
 * Balance per {@code (accountId, currency)} (architecture.md § Balance Model,
 * fintech F2).
 *
 * <ul>
 *   <li>{@code ledgerMinor} — confirmed funds.</li>
 *   <li>{@code heldMinor} — sum of ACTIVE holds.</li>
 *   <li>{@code available = ledger − held} — never negative.</li>
 * </ul>
 *
 * <p><b>F2 single-writer</b>: the only mutators are {@link #placeHold},
 * {@link #captureHold}, {@link #releaseHold}, {@link #credit} and
 * {@link #debit}, and they are reached exclusively through
 * {@code AccountApplicationService}. There is no balance setter. Every mutation
 * preserves {@code available ≥ 0} and {@code held ≥ 0}. Money is integer minor
 * units only (F5).
 */
@Entity
@Table(name = "balances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Balance {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "ledger_minor", nullable = false)
    private long ledgerMinor;

    @Column(name = "held_minor", nullable = false)
    private long heldMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Balance open(String id,
                               String accountId,
                               String tenantId,
                               Currency currency,
                               Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(currency, "currency");
        Balance b = new Balance();
        b.id = id;
        b.accountId = accountId;
        b.tenantId = tenantId;
        b.currency = currency;
        b.ledgerMinor = 0L;
        b.heldMinor = 0L;
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }

    public Money ledger() {
        return Money.of(ledgerMinor, currency);
    }

    public Money held() {
        return Money.of(heldMinor, currency);
    }

    /** available = ledger − held (F2 invariant). Never negative by construction. */
    public Money available() {
        return Money.of(ledgerMinor - heldMinor, currency);
    }

    private void ensureSameCurrency(Money amount) {
        if (amount.currency() != this.currency) {
            throw new com.example.finance.account.domain.error.DomainErrors
                    .CurrencyMismatchException(
                    "operation currency " + amount.currency()
                            + " != balance currency " + this.currency);
        }
    }

    /** Reserve {@code amount} against available funds (F2). */
    public void placeHold(Money amount, Instant now) {
        ensureSameCurrency(amount);
        long newHeld = Math.addExact(this.heldMinor, amount.minorUnits());
        if (this.ledgerMinor - newHeld < 0) {
            throw new InsufficientAvailableBalanceException(
                    "available " + (this.ledgerMinor - this.heldMinor)
                            + " < requested hold " + amount.minorUnits());
        }
        this.heldMinor = newHeld;
        this.updatedAt = now;
    }

    /**
     * Capture a settled hold: ledger −= captured; held −= original hold amount
     * (the un-captured remainder is implicitly released because it leaves
     * {@code held}). Preserves {@code ledger ≥ 0} and {@code held ≥ 0}.
     */
    public void captureHold(Money holdAmount, Money capturedAmount, Instant now) {
        ensureSameCurrency(holdAmount);
        ensureSameCurrency(capturedAmount);
        long newLedger = Math.subtractExact(this.ledgerMinor, capturedAmount.minorUnits());
        long newHeld = Math.subtractExact(this.heldMinor, holdAmount.minorUnits());
        if (newLedger < 0 || newHeld < 0) {
            throw new InsufficientAvailableBalanceException(
                    "capture would drive ledger/held negative");
        }
        this.ledgerMinor = newLedger;
        this.heldMinor = newHeld;
        this.updatedAt = now;
    }

    /** Release a hold: held −= hold amount; funds return to available. */
    public void releaseHold(Money holdAmount, Instant now) {
        ensureSameCurrency(holdAmount);
        long newHeld = Math.subtractExact(this.heldMinor, holdAmount.minorUnits());
        if (newHeld < 0) {
            throw new InsufficientAvailableBalanceException(
                    "release would drive held negative");
        }
        this.heldMinor = newHeld;
        this.updatedAt = now;
    }

    /** Credit confirmed funds (transfer-in target / internal top-up source). */
    public void credit(Money amount, Instant now) {
        ensureSameCurrency(amount);
        this.ledgerMinor = Math.addExact(this.ledgerMinor, amount.minorUnits());
        this.updatedAt = now;
    }

    /** Debit confirmed funds directly (guarded by available; v1 internal use). */
    public void debit(Money amount, Instant now) {
        ensureSameCurrency(amount);
        long newLedger = Math.subtractExact(this.ledgerMinor, amount.minorUnits());
        if (newLedger - this.heldMinor < 0) {
            throw new InsufficientAvailableBalanceException(
                    "available < debit amount " + amount.minorUnits());
        }
        this.ledgerMinor = newLedger;
        this.updatedAt = now;
    }
}
