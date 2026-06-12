package com.example.finance.ledger.infrastructure.outbox;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit-tests the append-side GL/AP-feed publisher (AC-1/AC-2/AC-5). Asserts the
 * exact canonical envelope + payload for both events (entry.posted incl. a
 * reversal → reversalOfEntryId present; period.closed) and that a {@code
 * ledger_outbox} row is saved with the right eventType / aggregateType /
 * partitionKey. Money is asserted as a minor-units STRING (F5 — never a float).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxLedgerEventPublisherTest {

    private static final String TENANT = "finance";
    private static final Money KRW_150K = Money.of(150_000L, Currency.KRW);
    private static final Instant POSTED_AT = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-12T00:00:00Z");

    @Mock LedgerOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxLedgerEventPublisher publisher;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        publisher = new OutboxLedgerEventPublisher(outboxRepository, objectMapper, fixed);
    }

    private static JournalEntry topupEntry() {
        return JournalEntry.post("entry-1", TENANT, POSTED_AT,
                SourceRef.ofTransaction("txn-1", "evt-1"), List.of(
                        JournalLine.debit(TENANT, LedgerAccountCodes.CASH_CLEARING, KRW_150K),
                        JournalLine.credit(TENANT,
                                LedgerAccountCodes.customerWallet("acc-1"), KRW_150K)));
    }

    private LedgerOutboxJpaEntity captureSaved() {
        ArgumentCaptor<LedgerOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(LedgerOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private JsonNode envelopeOf(LedgerOutboxJpaEntity row) throws Exception {
        return objectMapper.readTree(row.getPayload());
    }

    @Test
    @DisplayName("entry.posted: canonical envelope + balanced-lines payload, money minor-string")
    void entryPostedEnvelopeAndPayload() throws Exception {
        publisher.publishEntryPosted(topupEntry());

        LedgerOutboxJpaEntity row = captureSaved();
        assertThat(row.getEventType()).isEqualTo("finance.ledger.entry.posted");
        assertThat(row.getAggregateType()).isEqualTo("JournalEntry");
        assertThat(row.getAggregateId()).isEqualTo("entry-1");
        assertThat(row.getPartitionKey()).isEqualTo("entry-1");
        assertThat(row.getEventVersion()).isEqualTo("v1");
        assertThat(row.getEventId()).isNotNull();
        assertThat(row.getOccurredAt()).isEqualTo(NOW);

        JsonNode env = envelopeOf(row);
        assertThat(env.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(env.get("eventType").asText()).isEqualTo("finance.ledger.entry.posted");
        assertThat(env.get("occurredAt").asText()).isEqualTo(NOW.toString());
        assertThat(env.get("tenantId").asText()).isEqualTo("finance");
        assertThat(env.get("source").asText()).isEqualTo("finance-platform-ledger-service");
        assertThat(env.get("aggregateType").asText()).isEqualTo("JournalEntry");
        assertThat(env.get("aggregateId").asText()).isEqualTo("entry-1");

        JsonNode payload = env.get("payload");
        assertThat(payload.get("entryId").asText()).isEqualTo("entry-1");
        assertThat(payload.get("postedAt").asText()).isEqualTo(POSTED_AT.toString());
        assertThat(payload.get("reversalOfEntryId").isNull()).isTrue();

        JsonNode lines = payload.get("lines");
        assertThat(lines).hasSize(2);
        JsonNode debit = lines.get(0);
        assertThat(debit.get("ledgerAccountCode").asText()).isEqualTo("CASH_CLEARING");
        assertThat(debit.get("direction").asText()).isEqualTo("DEBIT");
        // F5: money amount is a minor-units STRING, not a number.
        assertThat(debit.get("money").get("amount").isTextual()).isTrue();
        assertThat(debit.get("money").get("amount").asText()).isEqualTo("150000");
        assertThat(debit.get("money").get("currency").asText()).isEqualTo("KRW");
        JsonNode credit = lines.get(1);
        assertThat(credit.get("ledgerAccountCode").asText()).isEqualTo("CUSTOMER_WALLET:acc-1");
        assertThat(credit.get("direction").asText()).isEqualTo("CREDIT");

        JsonNode source = payload.get("source");
        assertThat(source.get("sourceType").asText()).isEqualTo("TRANSACTION");
        assertThat(source.get("sourceTransactionId").asText()).isEqualTo("txn-1");
        assertThat(source.get("sourceEventId").asText()).isEqualTo("evt-1");
    }

    @Test
    @DisplayName("entry.posted for a reversal: reversalOfEntryId is present in the payload")
    void reversalEntryCarriesReversalOfEntryId() throws Exception {
        JournalEntry reversal = JournalEntry.reversalEntry("entry-r", POSTED_AT,
                SourceRef.ofTransaction("txn-rev", "evt-rev"), topupEntry());

        publisher.publishEntryPosted(reversal);

        JsonNode payload = envelopeOf(captureSaved()).get("payload");
        assertThat(payload.get("entryId").asText()).isEqualTo("entry-r");
        assertThat(payload.get("reversalOfEntryId").asText()).isEqualTo("entry-1");
        // The reversal swaps DEBIT↔CREDIT — CASH_CLEARING is now CREDIT.
        JsonNode lines = payload.get("lines");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).get("ledgerAccountCode").asText()).isEqualTo("CASH_CLEARING");
        assertThat(lines.get(0).get("direction").asText()).isEqualTo("CREDIT");
    }

    @Test
    @DisplayName("period.closed: {periodId, from, to, closedAt, entryCount} payload")
    void periodClosedEnvelopeAndPayload() throws Exception {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        AccountingPeriod period = AccountingPeriod.open("period-1", TENANT, from, to);
        period.close(NOW, "user-1", 7L);

        publisher.publishPeriodClosed(period);

        LedgerOutboxJpaEntity row = captureSaved();
        assertThat(row.getEventType()).isEqualTo("finance.ledger.period.closed");
        assertThat(row.getAggregateType()).isEqualTo("AccountingPeriod");
        assertThat(row.getAggregateId()).isEqualTo("period-1");
        assertThat(row.getPartitionKey()).isEqualTo("period-1");

        JsonNode env = envelopeOf(row);
        assertThat(env.get("eventType").asText()).isEqualTo("finance.ledger.period.closed");
        assertThat(env.get("aggregateType").asText()).isEqualTo("AccountingPeriod");
        assertThat(env.get("source").asText()).isEqualTo("finance-platform-ledger-service");

        JsonNode payload = env.get("payload");
        assertThat(payload.get("periodId").asText()).isEqualTo("period-1");
        assertThat(payload.get("from").asText()).isEqualTo(from.toString());
        assertThat(payload.get("to").asText()).isEqualTo(to.toString());
        assertThat(payload.get("closedAt").asText()).isEqualTo(NOW.toString());
        assertThat(payload.get("entryCount").asLong()).isEqualTo(7L);
    }
}
