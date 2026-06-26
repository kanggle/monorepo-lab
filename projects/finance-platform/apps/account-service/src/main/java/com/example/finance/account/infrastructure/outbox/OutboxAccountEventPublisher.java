package com.example.finance.account.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.finance.account.application.event.AccountEventPublisher;
import com.example.finance.account.domain.account.Account;
import com.example.finance.account.domain.account.ActorType;
import com.example.finance.account.domain.account.KycLevel;
import com.example.finance.account.domain.account.status.AccountStatus;
import com.example.finance.account.domain.balance.Balance;
import com.example.finance.account.domain.balance.Hold;
import com.example.finance.account.domain.money.Money;
import com.example.finance.account.domain.transaction.Transaction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link AccountEventPublisher} implementation (TASK-FIN-BE-045 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists an {@code account_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring ledger-service's
 * {@code OutboxLedgerEventPublisher}). The {@link AccountOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers dedupe on the
 * envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "finance-platform-account-service"}, every payload
 * field/order unchanged — so {@code ledger-service} and other {@code finance.*}
 * consumers are unaffected (TASK-FIN-BE-045 AC-2 / F2). The only change: the
 * envelope {@code eventId} now equals the {@code account_outbox} PK (both
 * UUIDv7) so the Kafka {@code eventId} header matches the payload.
 *
 * <p>Money is always {@code {amount:"<minor-units-string>", currency}} (F5 —
 * never a float); payloads carry ids + amounts only, no regulated PII (F7).
 */
@Component
public class OutboxAccountEventPublisher implements AccountEventPublisher {

    static final String SOURCE = "finance-platform-account-service";
    static final String EVENT_VERSION = "v1";
    static final String AGG_ACCOUNT = "account";
    static final String AGG_TRANSACTION = "transaction";

    private final AccountOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxAccountEventPublisher(AccountOutboxJpaRepository outboxRepository,
                                       ObjectMapper objectMapper,
                                       Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private static Map<String, Object> money(Money m) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("amount", m.toMinorString());
        o.put("currency", m.currency().code());
        return o;
    }

    @Override
    public void publishAccountOpened(Account a) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("ownerRef", a.getOwnerRef());
        p.put("currency", a.getCurrency().code());
        p.put("kycLevel", a.getKycLevel().name());
        p.put("status", a.getStatus().name());
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_OPENED, p);
    }

    @Override
    public void publishKycUpgraded(Account a, KycLevel from, KycLevel to,
                                   AccountStatus resultingStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("fromLevel", from.name());
        p.put("toLevel", to.name());
        p.put("resultingStatus", resultingStatus.name());
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_KYC_UPGRADED, p);
    }

    @Override
    public void publishStatusChanged(Account a, AccountStatus from, AccountStatus to,
                                     ActorType actorType, String reason) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", a.getId());
        p.put("fromStatus", from.name());
        p.put("toStatus", to.name());
        p.put("actorType", actorType.name());
        p.put("reason", reason);
        writeEvent(AGG_ACCOUNT, a.getId(), EVENT_ACCOUNT_STATUS_CHANGED, p);
    }

    @Override
    public void publishBalanceHeld(Hold hold, String transactionId, Balance balance) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, hold.amount(), balance);
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_HELD, p);
    }

    @Override
    public void publishBalanceCaptured(Hold hold, String transactionId, Balance balance,
                                       Money captured, Money released) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, captured, balance);
        p.put("released", money(released));
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_CAPTURED, p);
    }

    @Override
    public void publishBalanceReleased(Hold hold, String transactionId, Balance balance) {
        Map<String, Object> p = balanceBase(hold.getAccountId(), hold.getId(),
                transactionId, hold.amount(), balance);
        writeEvent(AGG_ACCOUNT, hold.getAccountId(), EVENT_BALANCE_RELEASED, p);
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

    @Override
    public void publishSettledAndCompleted(Transaction t) {
        publishTransactionSettled(t);
        publishTransactionCompleted(t);
    }

    @Override
    public void publishTransactionSettled(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_SETTLED, txnPayload(t));
    }

    @Override
    public void publishTransactionCompleted(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_COMPLETED, txnPayload(t));
    }

    @Override
    public void publishTransactionFailed(Transaction t) {
        writeEvent(AGG_TRANSACTION, t.getId(), EVENT_TRANSACTION_FAILED, txnPayload(t));
    }

    @Override
    public void publishTransactionReversed(Transaction reversal) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("transactionId", reversal.getId());
        p.put("reversalOfTransactionId", reversal.getReversalOfTransactionId());
        p.put("accountId", reversal.getAccountId());
        p.put("money", money(reversal.money()));
        writeEvent(AGG_TRANSACTION, reversal.getId(), EVENT_TRANSACTION_REVERSED, p);
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

    @Override
    public void publishSanctionHit(String accountId, String transactionId,
                                   String screeningRef, String queuedReviewId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("accountId", accountId);
        p.put("transactionId", transactionId);
        p.put("screeningRef", screeningRef);
        p.put("queuedReviewId", queuedReviewId);
        writeEvent(AGG_ACCOUNT, accountId, EVENT_COMPLIANCE_SANCTION_HIT, p);
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher} path), serialise it, and persist a pending
     * {@code account_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK.
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", 1);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(AccountOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType, EVENT_VERSION,
                json, aggregateId, occurredAt));
    }
}
