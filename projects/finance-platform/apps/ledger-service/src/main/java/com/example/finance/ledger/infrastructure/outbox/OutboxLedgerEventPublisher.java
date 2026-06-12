package com.example.finance.ledger.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.finance.ledger.application.port.outbound.LedgerEventPublisher;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.period.AccountingPeriod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link LedgerEventPublisher} implementation (3rd increment, TASK-FIN-BE-009 —
 * the GL/AP feed). Builds the canonical envelope (the same shape ledger-service's
 * own consumer parses — {@code {eventId, eventType, occurredAt, tenantId, source,
 * aggregateType, aggregateId, payload}}, {@code source =
 * "finance-platform-ledger-service"}) and persists a {@code ledger_outbox} row in
 * the caller's transaction. The {@code LedgerOutboxPublisher} relay forwards the
 * row to Kafka asynchronously; downstream consumers dedupe on the envelope
 * {@code eventId} (at-least-once).
 *
 * <p>Money is always {@code {amount:"<minor-units-string>", currency}} (F5 — never
 * a float/double); the payload carries ids + amounts only, no regulated PII (F7).
 * Event ids are UUIDv7 ({@link UuidV7}) so the outbox PK is time-ordered.
 */
@Component
public class OutboxLedgerEventPublisher implements LedgerEventPublisher {

    static final String SOURCE = "finance-platform-ledger-service";
    static final String EVENT_VERSION = "v1";

    static final String AGG_JOURNAL_ENTRY = "JournalEntry";
    static final String AGG_ACCOUNTING_PERIOD = "AccountingPeriod";

    static final String EVENT_ENTRY_POSTED = "finance.ledger.entry.posted";
    static final String EVENT_PERIOD_CLOSED = "finance.ledger.period.closed";

    private final LedgerOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxLedgerEventPublisher(LedgerOutboxJpaRepository outboxRepository,
                                      ObjectMapper objectMapper,
                                      Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishEntryPosted(JournalEntry entry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entryId", entry.entryId());
        payload.put("postedAt", entry.postedAt().toString());
        payload.put("lines", lines(entry));
        payload.put("source", source(entry.source()));
        payload.put("reversalOfEntryId", entry.reversalOfEntryId());
        append(EVENT_ENTRY_POSTED, AGG_JOURNAL_ENTRY, entry.entryId(),
                entry.tenantId(), entry.entryId(), payload);
    }

    @Override
    public void publishPeriodClosed(AccountingPeriod period) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("periodId", period.periodId());
        payload.put("from", period.from().toString());
        payload.put("to", period.to().toString());
        payload.put("closedAt", period.closedAt() == null ? null : period.closedAt().toString());
        payload.put("entryCount", period.entryCount());
        append(EVENT_PERIOD_CLOSED, AGG_ACCOUNTING_PERIOD, period.periodId(),
                period.tenantId(), period.periodId(), payload);
    }

    private static List<Map<String, Object>> lines(JournalEntry entry) {
        List<Map<String, Object>> lines = new ArrayList<>(entry.lines().size());
        for (JournalLine line : entry.lines()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("ledgerAccountCode", line.ledgerAccountCode());
            l.put("direction", line.direction().name());
            l.put("money", money(line));
            lines.add(l);
        }
        return lines;
    }

    private static Map<String, Object> money(JournalLine line) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amount", line.money().toMinorString());
        m.put("currency", line.currency().code());
        return m;
    }

    private static Map<String, Object> source(SourceRef source) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sourceType", source.getSourceType());
        s.put("sourceTransactionId", source.getSourceTransactionId());
        s.put("sourceEventId", source.getSourceEventId());
        return s;
    }

    /**
     * Wrap the payload in the canonical envelope, serialise it, and persist a
     * pending {@code ledger_outbox} row in the caller's transaction.
     */
    private void append(String eventType, String aggregateType, String aggregateId,
                        String tenantId, String partitionKey, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("tenantId", tenantId);
        envelope.put("source", SOURCE);
        envelope.put("aggregateType", aggregateType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(LedgerOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType, EVENT_VERSION,
                json, partitionKey, occurredAt));
    }
}
