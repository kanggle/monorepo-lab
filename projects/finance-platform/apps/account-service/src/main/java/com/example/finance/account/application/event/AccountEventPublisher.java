package com.example.finance.account.application.event;

import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.ActorType;
import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.Transaction;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends {@code finance.account.* / finance.balance.* / finance.transaction.*
 * / finance.compliance.*} events to the transactional outbox (transactional
 * T3, fintech F1). Every {@code publish*} call happens INSIDE the use-case
 * {@code @Transactional} boundary so the event write commits atomically with
 * the balance + txn-state change (F1) — there is no separate publish path.
 *
 * <p>Money payloads are {@code {amount: "<minor-string>", currency}} (F5 —
 * never a float). No regulated PII is carried (F7) — only accountId / ownerRef
 * (opaque) / non-PII screening ref.
 *
 * <p>Contract: {@code specs/contracts/events/finance-account-events.md}.
 */
@Component
public class AccountEventPublisher extends BaseEventPublisher {

    private static final String SOURCE = "finance-platform-account-service";
    private static final String AGG_ACCOUNT = "account";
    private static final String AGG_TRANSACTION = "transaction";

    public static final String EVENT_ACCOUNT_OPENED = "finance.account.opened";
    public static final String EVENT_ACCOUNT_KYC_UPGRADED = "finance.account.kyc.upgraded";
    public static final String EVENT_ACCOUNT_STATUS_CHANGED = "finance.account.status.changed";
    public static final String EVENT_BALANCE_HELD = "finance.balance.held";
    public static final String EVENT_BALANCE_CAPTURED = "finance.balance.captured";
    public static final String EVENT_BALANCE_RELEASED = "finance.balance.released";
    public static final String EVENT_TRANSACTION_SETTLED = "finance.transaction.settled";
    public static final String EVENT_TRANSACTION_COMPLETED = "finance.transaction.completed";
    public static final String EVENT_TRANSACTION_FAILED = "finance.transaction.failed";
    public static final String EVENT_TRANSACTION_REVERSED = "finance.transaction.reversed";
    public static final String EVENT_COMPLIANCE_SANCTION_HIT = "finance.compliance.sanction.hit";

    public AccountEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    private static Map<String, Object> money(Money m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("amount", m.toMinorString());
        o.put("currency", m.currency().code());
        return o;
    }

    public void publishAccountOpened(Account a) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("ownerRef", a.getOwnerRef());
        p.put("currency", a.getCurrency().code());
        p.put("kycLevel", a.getKycLevel().name());
        p.put("status", a.getStatus().name());
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_OPENED, SOURCE, p);
    }

    public void publishKycUpgraded(Account a, KycLevel from, KycLevel to,
                                   AccountStatus resultingStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("fromLevel", from.name());
        p.put("toLevel", to.name());
        p.put("resultingStatus", resultingStatus.name());
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_KYC_UPGRADED, SOURCE, p);
    }

    public void publishStatusChanged(Account a, AccountStatus from, AccountStatus to,
                                     ActorType actorType, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("fromStatus", from.name());
        p.put("toStatus", to.name());
        p.put("actorType", actorType.name());
        p.put("reason", reason);
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_STATUS_CHANGED, SOURCE, p);
    }

    public void publishBalanceHeld(Hold hold, String transactionId, Balance balance) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, hold.amount(), balance);
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_HELD, SOURCE, p);
    }

    public void publishBalanceCaptured(Hold hold, String transactionId, Balance balance,
                                       Money captured, Money released) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, captured, balance);
        p.put("released", money(released));
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_CAPTURED, SOURCE, p);
    }

    public void publishBalanceReleased(Hold hold, String transactionId, Balance balance) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, hold.amount(), balance);
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_RELEASED, SOURCE, p);
    }

    private Map<String, Object> balanceBase(String accountId, String holdId,
                                            String transactionId, Money amount,
                                            Balance balance) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", accountId);
        p.put("holdId", holdId);
        p.put("transactionId", transactionId);
        p.put("money", money(amount));
        p.put("available", money(balance.available()));
        return p;
    }

    public void publishTransactionSettled(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_SETTLED, SOURCE, txnPayload(t));
    }

    public void publishTransactionCompleted(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_COMPLETED, SOURCE, txnPayload(t));
    }

    public void publishTransactionFailed(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_FAILED, SOURCE, txnPayload(t));
    }

    public void publishTransactionReversed(Transaction reversal) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("transactionId", reversal.getId());
        p.put("reversalOfTransactionId", reversal.getReversalOfTransactionId());
        p.put("accountId", reversal.getAccountId());
        p.put("money", money(reversal.money()));
        writeEvent(AGG_TRANSACTION, reversal.getId(), EVENT_TRANSACTION_REVERSED, SOURCE, p);
    }

    private Map<String, Object> txnPayload(Transaction t) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("transactionId", t.getId());
        p.put("accountId", t.getAccountId());
        p.put("type", t.getType().name());
        p.put("money", money(t.money()));
        p.put("counterpartyAccountId", t.getCounterpartyAccountId());
        p.put("status", t.getStatus().name());
        p.put("failureCode", t.getFailureCode());
        return p;
    }

    public void publishSanctionHit(String accountId, String transactionId,
                                   String screeningRef, String queuedReviewId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", accountId);
        p.put("transactionId", transactionId);
        p.put("screeningRef", screeningRef);
        p.put("queuedReviewId", queuedReviewId);
        writeEvent(AGG_ACCOUNT, accountId, EVENT_COMPLIANCE_SANCTION_HIT, SOURCE, p);
    }
}
