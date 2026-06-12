package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.domain.money.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One per-account row of an {@link com.example.finance.ledger.domain.period.PeriodBalanceSnapshot}
 * close-time snapshot ({@code period_balance_snapshot}). Insert-only (F3/F6
 * parity) — the grand totals are computed on read by summing the rows (no header
 * row stored). Money is BIGINT minor units + currency VARCHAR(3) (F5).
 */
@Entity
@Table(name = "period_balance_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodBalanceSnapshotJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_id", length = 36, nullable = false)
    private String periodId;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ledger_account_code", length = 100, nullable = false)
    private String ledgerAccountCode;

    @Column(name = "debit_minor", nullable = false)
    private long debitMinor;

    @Column(name = "credit_minor", nullable = false)
    private long creditMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    private PeriodBalanceSnapshotJpaEntity(String periodId, String tenantId,
                                           String ledgerAccountCode, long debitMinor,
                                           long creditMinor, Currency currency) {
        this.periodId = periodId;
        this.tenantId = tenantId;
        this.ledgerAccountCode = ledgerAccountCode;
        this.debitMinor = debitMinor;
        this.creditMinor = creditMinor;
        this.currency = currency;
    }

    public static PeriodBalanceSnapshotJpaEntity of(String periodId, String tenantId,
                                                    String ledgerAccountCode, long debitMinor,
                                                    long creditMinor, Currency currency) {
        return new PeriodBalanceSnapshotJpaEntity(periodId, tenantId, ledgerAccountCode,
                debitMinor, creditMinor, currency);
    }

    public String periodId() {
        return periodId;
    }

    public String ledgerAccountCode() {
        return ledgerAccountCode;
    }

    public long debitMinor() {
        return debitMinor;
    }

    public long creditMinor() {
        return creditMinor;
    }

    public Currency currency() {
        return currency;
    }
}
