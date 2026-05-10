package com.example.messaging.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractOutboxPublisher")
class AbstractOutboxPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OutboxMetrics metrics;

    private InMemoryRepo repo;
    private TestPublisher publisher;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repo = new InMemoryRepo();
        clock = Clock.fixed(Instant.parse("2026-05-10T00:00:10Z"), ZoneOffset.UTC);
        TopicResolver resolver = eventType -> "topic." + eventType;
        publisher = new TestPublisher(repo, kafkaTemplate, new SyncTransactionTemplate(),
                resolver, metrics, clock, 50);
    }

    @Test
    @DisplayName("publishes pending rows and marks them published")
    void publishesAndMarks() {
        UUID id = UUID.randomUUID();
        TestRow row = new TestRow(id, "x.y", "Agg", "agg-1",
                "{\"a\":1}", Instant.parse("2026-05-10T00:00:00Z"));
        repo.add(row);

        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> sent = captor.getValue();
        assertThat(sent.topic()).isEqualTo("topic.x.y");
        assertThat(sent.key()).isEqualTo("agg-1");
        assertThat(sent.value()).isEqualTo("{\"a\":1}");
        assertThat(sent.headers().lastHeader("eventId")).isNotNull();
        assertThat(sent.headers().lastHeader("eventType")).isNotNull();

        assertThat(row.getPublishedAt()).isNotNull();
        verify(metrics).recordPublishSuccess(eq("x.y"), any(Duration.class));
    }

    @Test
    @DisplayName("uses partitionKey when provided in preference to aggregateId")
    void prefersPartitionKey() {
        UUID id = UUID.randomUUID();
        TestRow row = new TestRow(id, "x.y", "Agg", "agg-1",
                "{}", Instant.parse("2026-05-10T00:00:00Z"));
        row.partitionKey = "saga-123";
        repo.add(row);

        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("saga-123");
    }

    @Test
    @DisplayName("on Kafka failure leaves row pending and records failure metric")
    void kafkaFailureLeavesRowPending() {
        UUID id = UUID.randomUUID();
        TestRow row = new TestRow(id, "x.y", "Agg", "agg-1",
                "{}", Instant.parse("2026-05-10T00:00:00Z"));
        repo.add(row);

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed);

        publisher.publishPending();

        assertThat(row.getPublishedAt()).isNull();
        verify(metrics).recordPublishFailure(eq("x.y"), anyString());
        verify(metrics, never()).recordPublishSuccess(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("backoff window suppresses subsequent publish attempts")
    void backoffSuppressesAttempts() {
        UUID id = UUID.randomUUID();
        TestRow row = new TestRow(id, "x.y", "Agg", "agg-1",
                "{}", Instant.parse("2026-05-10T00:00:00Z"));
        repo.add(row);

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafkaTemplate.send(any(ProducerRecord.class))).willReturn(failed);

        publisher.publishPending(); // first attempt: fails, opens backoff
        // Second attempt within the same instant: should skip the repository call
        publisher.publishPending();

        // KafkaTemplate.send invoked only once (second tick was suppressed by backoff)
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    @DisplayName("no pending rows: no Kafka call, no metrics")
    void noPendingNoCalls() {
        publisher.publishPending();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(metrics, never()).recordPublishSuccess(anyString(), any(Duration.class));
    }

    // Test fixtures ----------------------------------------------------------------

    private static class TestRow implements OutboxRow {
        final UUID eventId;
        final String eventType;
        final String aggregateType;
        final String aggregateId;
        String partitionKey;
        final String payload;
        final Instant occurredAt;
        Instant publishedAt;

        TestRow(UUID eventId, String eventType, String aggregateType, String aggregateId,
                String payload, Instant occurredAt) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            this.payload = payload;
            this.occurredAt = occurredAt;
        }

        @Override public UUID getEventId() { return eventId; }
        @Override public String getEventType() { return eventType; }
        @Override public String getAggregateType() { return aggregateType; }
        @Override public String getAggregateId() { return aggregateId; }
        @Override public String getPartitionKey() { return partitionKey; }
        @Override public String getPayload() { return payload; }
        @Override public Instant getOccurredAt() { return occurredAt; }
        @Override public Instant getPublishedAt() { return publishedAt; }
        @Override public void markPublished(Instant at) { this.publishedAt = at; }
    }

    private static class InMemoryRepo implements OutboxRowRepository<TestRow> {
        private final Map<UUID, TestRow> rows = new HashMap<>();
        private final List<TestRow> order = new ArrayList<>();

        void add(TestRow r) {
            rows.put(r.eventId, r);
            order.add(r);
        }

        @Override
        public List<TestRow> findPending(int batchSize) {
            return order.stream().filter(r -> r.publishedAt == null).limit(batchSize).toList();
        }

        @Override
        public TestRow findById(UUID id) { return rows.get(id); }

        @Override
        public void save(TestRow row) { rows.put(row.eventId, row); }

        @Override
        public long countPending() {
            return order.stream().filter(r -> r.publishedAt == null).count();
        }
    }

    /**
     * Synchronous TransactionTemplate stub that runs callbacks immediately without
     * wrapping in an actual transaction. Sufficient for unit tests of the publisher
     * loop because the real TX semantics are exercised in integration tests.
     */
    private static class SyncTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
            action.accept(null);
        }
    }

    private static class TestPublisher extends AbstractOutboxPublisher<TestRow> {
        TestPublisher(OutboxRowRepository<TestRow> repository,
                      KafkaTemplate<String, String> kafkaTemplate,
                      TransactionTemplate transactionTemplate,
                      TopicResolver topicResolver,
                      OutboxMetrics metrics,
                      Clock clock,
                      int batchSize) {
            super(repository, kafkaTemplate, transactionTemplate, topicResolver, metrics, clock, batchSize);
        }
    }
}
