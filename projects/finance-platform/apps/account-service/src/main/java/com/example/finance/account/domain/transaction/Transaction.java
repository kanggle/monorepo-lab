package com.example.finance.account.domain.transaction;

import com.example.finance.account.domain.error.DomainErrors.TransactionAlreadySettledException;
import com.example.finance.account.domain.money.Currency;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import com.example.finance.account.domain.transaction.status.TransactionStatusMachine;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Transaction aggregate root (architecture.md § Transaction State Machine,
 * fintech F1/F3). Drives only through {@link TransactionStatusMachine}; a
 * SETTLED/COMPLETED txn is immutable — {@link #ensureMutable} rejects in-place
 * mutation, correction is a NEW {@link TransactionType#REVERSAL} txn that
 * references the original via {@code reversalOfTransactionId} (F3).
 *
 * <p>Money is integer minor units only (F5). JPA annotations are the allowed
 * domain↔framework exception; the transition logic is pure.
 */
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", length = 20, nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private TransactionStatus status;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private Currency currency;

    @Column(name = "counterparty_account_id", length = 36)
    private String counterpartyAccountId;

    @Column(name = "hold_id", length = 36)
    private String holdId;

    @Column(name = "reversal_of_transaction_id", length = 36)
    private String reversalOfTransactionId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "reason", length = 256)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Transaction request(String id,
                                      String tenantId,
                                      String accountId,
                                      TransactionType type,
                                      Money amount,
                                      String counterpartyAccountId,
                                      String holdId,
                                      String reason,
                                      Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(amount, "amount");
        Transaction t = new Transaction();
        t.id = id;
        t.tenantId = tenantId;
        t.accountId = accountId;
        t.type = type;
        t.status = TransactionStatus.REQUESTED;
        t.amountMinor = amount.minorUnits();
        t.currency = amount.currency();
        t.counterpartyAccountId = counterpartyAccountId;
        t.holdId = holdId;
        t.reason = reason;
        t.createdAt = now;
        return t;
    }

    public static Transaction reversalOf(String id,
                                         Transaction original,
                                         Instant now) {
        Transaction t = request(id, original.tenantId, original.accountId,
                TransactionType.REVERSAL, original.money(),
                original.counterpartyAccountId, original.holdId,
                "reversal of " + original.id, now);
        t.reversalOfTransactionId = original.id;
        return t;
    }

    public Money money() {
        return Money.of(amountMinor, currency);
    }

    private void ensureMutable() {
        if (status.isImmutable()) {
            throw new TransactionAlreadySettledException(
                    "Transaction " + id + " is " + status
                            + " — correction is reversal-only (F3)");
        }
    }

    private void transition(TransactionStatus target) {
        ensureMutable();
        TransactionStatusMachine.ensureTransitionAllowed(this.status, target);
        this.status = target;
    }

    public void markValidated() {
        transition(TransactionStatus.VALIDATED);
    }

    public void markAuthorized() {
        transition(TransactionStatus.AUTHORIZED);
    }

    public void markSettled(Instant now) {
        transition(TransactionStatus.SETTLED);
        this.settledAt = now;
    }

    public void markCompleted() {
        // SETTLED → COMPLETED is the one allowed post-settle transition; it is
        // a status-only finalization (the funds are already moved). Bypass the
        // immutability guard for this specific edge — the matrix still gates it.
        TransactionStatusMachine.ensureTransitionAllowed(this.status, TransactionStatus.COMPLETED);
        this.status = TransactionStatus.COMPLETED;
    }

    public void markFailed(String failureCode) {
        ensureMutable();
        TransactionStatusMachine.ensureTransitionAllowed(this.status, TransactionStatus.FAILED);
        this.status = TransactionStatus.FAILED;
        this.failureCode = failureCode;
    }

    /** Mark the ORIGINAL txn as reversed (only its status flips; amounts stay). */
    public void markReversed() {
        TransactionStatusMachine.ensureTransitionAllowed(this.status, TransactionStatus.REVERSED);
        this.status = TransactionStatus.REVERSED;
    }
}
