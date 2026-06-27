package com.example.review;

import com.example.review.domain.event.ReviewCreatedPayload;
import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewEventPublisher;
import com.example.review.infrastructure.event.ReviewOutboxPublisher;
import com.example.review.infrastructure.event.ReviewOutboxRepository;
import com.example.review.infrastructure.event.dto.ReviewEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test for the review-service outbox v2 relay
 * (TASK-BE-445). Proves: the write path persists a {@code review_outbox} row →
 * {@link ReviewOutboxPublisher} drains it to Kafka with the {@code eventId}/{@code eventType}
 * headers + preserved key ({@code reviewId}) + value (byte-identical
 * ReviewEventMessage envelope) → the row's {@code published_at} is set.
 *
 * <p><b>Verification note (TASK-BE-445 AC-7):</b> review-service has no
 * Testcontainers CI lane (only order/payment do), so this {@code @Tag("integration")}
 * test is excluded from the Docker-free {@code :test} task and does not run in CI
 * today. Authored for compile-time verification + activation if a lane is added.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "review.review.created")
@DisplayName("review outbox v2 relay 통합 테스트")
class ReviewOutboxRelayIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("review_db")
            .withUsername("review_user")
            .withPassword("review_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ReviewEventPublisher writePath;

    @Autowired
    private ReviewOutboxPublisher relay;

    @Autowired
    private ReviewOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("write path 가 review_outbox 행을 적재하고 relay 가 헤더+키+값 보존하여 발행한다")
    void writeThenRelay_publishesWithHeadersAndPreservedKeyValue_thenMarksPublished() throws Exception {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-it-1", "product-it-1", "user-it-1", 5, "2026-06-27T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, "ecommerce", clock);
        // Mirror OutboxReviewEventPublisher.toMessage(...) — the wire envelope.
        String expectedPayload = objectMapper.writeValueAsString(new ReviewEventMessage(
                event.eventId(), event.eventType(), event.occurredAt(),
                event.source(), event.tenantId(), event.payload()));

        writePath.publish(event);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("review-it", "true", broker),
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "review.review.created");

        relay.publishPending();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "review.review.created", ofSeconds(10));
        assertThat(record.key()).isEqualTo("review-it-1");
        assertThat(record.value()).isEqualTo(expectedPayload);
        assertThat(new String(record.headers().lastHeader("eventId").value()))
                .isEqualTo(event.eventId().toString());
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .isEqualTo("ReviewCreated");
        consumer.close();

        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }
}
