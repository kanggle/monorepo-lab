package com.example.scmplatform.procurement.domain.po;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object: money amount + currency code. Embedded into the
 * {@link PurchaseOrder} aggregate so JPA persists {@code total_amount} +
 * {@code currency} as separate columns while the domain treats them as one.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Money {

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative: " + amount);
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code: " + currency);
        }
        return new Money(amount, currency.toUpperCase());
    }

    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + this.currency + " vs " + other.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public static Money zero(String currency) {
        return of(BigDecimal.ZERO, currency);
    }
}
