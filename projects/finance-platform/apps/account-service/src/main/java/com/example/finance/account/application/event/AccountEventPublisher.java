package com.example.finance.account.application.event;

import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.ActorType;
import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.Transaction;

/**
 * Application port for appending {@code finance.account.* / finance.balance.* /
 * finance.transaction.* / finance.compliance.*} events to the transactional
 * outbox (transactional T3, fintech F1). Every {@code publish*} call happens
 * INSIDE the use-case {@code @Transactional} boundary so the event write commits
 * atomically with the balance + txn-state change (F1) — there is no separate
 * publish path.
 *
 * <p>TASK-FIN-BE-045 (outbox v1 → v2): this is now an application-layer port; the
 * implementation is {@code infrastructure.outbox.OutboxAccountEventPublisher},
 * which builds the canonical envelope and persists an {@code account_outbox} row
 * (the {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5,
 * mirroring ledger-service). The on-wire envelope shape is preserved exactly from
 * the previous {@code BaseEventPublisher} path.
 *
 * <p>Money payloads are {@code {amount: "<minor-string>", currency}} (F5 —
 * never a float). No regulated PII is carried (F7) — only accountId / ownerRef
 * (opaque) / non-PII screening ref.
 *
 * <p>Contract: {@code specs/contracts/events/finance-account-events.md}.
 */
public interface AccountEventPublisher {

    String EVENT_ACCOUNT_OPENED = "finance.account.opened";
    String EVENT_ACCOUNT_KYC_UPGRADED = "finance.account.kyc.upgraded";
    String EVENT_ACCOUNT_STATUS_CHANGED = "finance.account.status.changed";
    String EVENT_BALANCE_HELD = "finance.balance.held";
    String EVENT_BALANCE_CAPTURED = "finance.balance.captured";
    String EVENT_BALANCE_RELEASED = "finance.balance.released";
    String EVENT_TRANSACTION_SETTLED = "finance.transaction.settled";
    String EVENT_TRANSACTION_COMPLETED = "finance.transaction.completed";
    String EVENT_TRANSACTION_FAILED = "finance.transaction.failed";
    String EVENT_TRANSACTION_REVERSED = "finance.transaction.reversed";
    String EVENT_COMPLIANCE_SANCTION_HIT = "finance.compliance.sanction.hit";

    void publishAccountOpened(Account a);

    void publishKycUpgraded(Account a, KycLevel from, KycLevel to, AccountStatus resultingStatus);

    void publishStatusChanged(Account a, AccountStatus from, AccountStatus to,
                              ActorType actorType, String reason);

    void publishBalanceHeld(Hold hold, String transactionId, Balance balance);

    void publishBalanceCaptured(Hold hold, String transactionId, Balance balance,
                                Money captured, Money released);

    void publishBalanceReleased(Hold hold, String transactionId, Balance balance);

    /**
     * Convenience helper that publishes both the SETTLED and COMPLETED events for
     * a single transaction — the invariant pair emitted by every successful
     * fund-movement use case (transactional T2, fintech F1).
     */
    void publishSettledAndCompleted(Transaction t);

    void publishTransactionSettled(Transaction t);

    void publishTransactionCompleted(Transaction t);

    void publishTransactionFailed(Transaction t);

    void publishTransactionReversed(Transaction reversal);

    void publishSanctionHit(String accountId, String transactionId,
                            String screeningRef, String queuedReviewId);
}
