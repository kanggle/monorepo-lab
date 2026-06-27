package com.example.account.infrastructure.outbox;

import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
import com.example.common.id.UuidV7;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the account-service v2 relay {@link AccountOutboxPublisher}
 * (TASK-BE-451). Replaces the v1 {@code AccountOutboxPollingSchedulerTest}: verifies
 * {@link AccountOutboxPublisher#topicFor} covers BOTH publishers' event types
 * (account.* lifecycle + tenant.subscription.changed — identically-named bare
 * topics, reject-unmapped) and a publish round-trip.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountOutboxPublisherTest {

    @Mock
    private AccountOutboxJpaRepository repository;

    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final TransactionTemplate transactionTemplate = new ImmediateTransactionTemplate();

    private AccountOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        lenient().when(repository.countByPublishedAtIsNull()).thenReturn(0L);
        publisher = new AccountOutboxPublisher(repository, kafkaTemplate, transactionTemplate,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC),
                meterRegistry, 100);
    }

    @Test
    @DisplayName("topicFor covers the AccountEventPublisher event types (bare topics)")
    void topicFor_accountLifecycleEvents() {
        assertThat(AccountOutboxPublisher.topicFor("account.created")).isEqualTo("account.created");
        assertThat(AccountOutboxPublisher.topicFor("account.status.changed")).isEqualTo("account.status.changed");
        assertThat(AccountOutboxPublisher.topicFor("account.locked")).isEqualTo("account.locked");
        assertThat(AccountOutboxPublisher.topicFor("account.unlocked")).isEqualTo("account.unlocked");
        assertThat(AccountOutboxPublisher.topicFor("account.roles.changed")).isEqualTo("account.roles.changed");
        assertThat(AccountOutboxPublisher.topicFor("account.deleted")).isEqualTo("account.deleted");
    }

    @Test
    @DisplayName("topicFor ALSO covers TenantDomainSubscriptionEventPublisher (tenant.subscription.changed)")
    void topicFor_subscriptionChanged() {
        assertThat(AccountOutboxPublisher.topicFor("tenant.subscription.changed"))
                .isEqualTo("tenant.subscription.changed");
    }

    @Test
    @DisplayName("topicFor rejects an unknown event type")
    void topicFor_unknown_throws() {
        assertThatThrownBy(() -> AccountOutboxPublisher.topicFor("unknown.event"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown account event type");
    }

    @Test
    @DisplayName("publishPending sends key=aggregateId + value=flat payload + headers, then marks published")
    void publishPending_roundTrip() {
        UUID id = UuidV7.randomUuid();
        AccountOutboxJpaEntity row = AccountOutboxJpaEntity.create(
                id, "Account", "acc-1", "account.locked",
                "{\"eventId\":\"" + id + "\",\"accountId\":\"acc-1\"}", "acc-1",
                Instant.parse("2026-04-14T09:59:59Z"));

        when(repository.findPending(any())).thenReturn(List.of(row));
        when(repository.findById(id)).thenReturn(Optional.of(row));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(ackFuture("account.locked"));

        publisher.publishPending();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("account.locked");
        assertThat(record.key()).isEqualTo("acc-1");
        assertThat(record.value()).isEqualTo("{\"eventId\":\"" + id + "\",\"accountId\":\"acc-1\"}");
        assertThat(new String(record.headers().lastHeader("eventId").value(), StandardCharsets.UTF_8))
                .isEqualTo(id.toString());
        assertThat(new String(record.headers().lastHeader("eventType").value(), StandardCharsets.UTF_8))
                .isEqualTo("account.locked");

        verify(repository).save(row);
        assertThat(row.getPublishedAt()).isNotNull();
    }

    private static CompletableFuture<SendResult<String, String>> ackFuture(String topic) {
        TopicPartition tp = new TopicPartition(topic, 0);
        RecordMetadata md = new RecordMetadata(tp, 0, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(
                new SendResult<>(new ProducerRecord<>(topic, "k", "v"), md));
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }

        @Override
        public void executeWithoutResult(java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action) {
            action.accept(new SimpleTransactionStatus());
        }
    }
}
