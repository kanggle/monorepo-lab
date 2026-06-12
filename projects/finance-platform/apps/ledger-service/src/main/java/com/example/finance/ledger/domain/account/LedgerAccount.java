package com.example.finance.ledger.domain.account;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Chart-of-accounts node (architecture.md § Chart of Accounts). The platform GL
 * accounts ({@code CASH_CLEARING}, {@code SETTLEMENT_SUSPENSE}) are seeded at
 * startup; per-customer wallet accounts ({@code CUSTOMER_WALLET:{accountId}})
 * are created lazily on first posting.
 *
 * <p>Pure domain semantics (running-balance interpretation by {@link NormalSide})
 * plus JPA annotations — the single allowed domain↔framework exception. There is
 * no setter; the chart node is immutable once created.
 */
@Entity
@Table(name = "ledger_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccount {

    @Id
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 20, nullable = false)
    private LedgerAccountType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "normal_side", length = 10, nullable = false)
    private NormalSide normalSide;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static LedgerAccount of(String code, String tenantId,
                                   LedgerAccountType type, Instant createdAt) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        LedgerAccount a = new LedgerAccount();
        a.code = code;
        a.tenantId = tenantId;
        a.type = type;
        a.normalSide = type.normalSide();
        a.createdAt = createdAt;
        return a;
    }

    /**
     * The running balance interpreted on the account's normal side: a positive
     * value means the net falls on {@link #normalSide()}. Computed as
     * {@code |debitTotal − creditTotal|}; the side it falls on is
     * {@link #balanceSide(Money, Money)}.
     */
    public Money runningBalance(Money debitTotal, Money creditTotal) {
        return debitTotal.absoluteDifference(creditTotal);
    }

    /** The side the net balance falls on (DEBIT if debits exceed credits). */
    public static NormalSide balanceSide(Money debitTotal, Money creditTotal) {
        return debitTotal.isGreaterThanOrEqual(creditTotal)
                ? NormalSide.DEBIT : NormalSide.CREDIT;
    }

    /** Convenience for a zero balance in this account's posting currency. */
    public Money zero(Currency currency) {
        return Money.zero(currency);
    }
}
