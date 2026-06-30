package com.example.promotion;

import com.example.promotion.application.event.CouponUsedEvent;
import com.example.promotion.application.port.PromotionEventPublisher;
import com.example.promotion.infrastructure.event.PromotionOutboxRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static java.time.Duration.ofSeconds;

/**
 * Round-trip integration test for the promotion-service outbox v2 relay
 * (TASK-BE-444). Proves the full path: the write path persists a
 * {@code promotion_outbox} row → {@link com.example.promotion.infrastructure.event.PromotionOutboxPublisher}
 * drains it to Kafka with the {@code eventId}/{@code eventType} headers and the
 * preserved key ({@code couponId}) + value (byte-identical envelope payload) →
 * the row's {@code published_at} is set.
 *
 * <p><b>Verification note (TASK-BE-444 AC-7):</b> originally promotion-service had no
 * Testcontainers CI lane (only order/payment did), so this {@code @Tag("integration")}
 * test was excluded from the Docker-free {@code :test} task. TASK-MONO-319 added a
 * dedicated {@code integrationTest} lane for promotion-service, so it now runs on CI.
 */
@SpringBootTest(classes = PromotionServiceApplication.class, properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "promotion.coupon.used")
@DisplayName("promotion outbox v2 relay 통합 테스트")
class PromotionOutboxRelayIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("promotion_db")
            .withUsername("promotion_user")
            .withPassword("promotion_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PromotionEventPublisher writePath;

    @Autowired
    private com.example.promotion.infrastructure.event.PromotionOutboxPublisher relay;

    @Autowired
    private PromotionOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("write path 가 promotion_outbox 행을 적재하고 relay 가 헤더+키+값 보존하여 발행한다")
    void writeThenRelay_publishesWithHeadersAndPreservedKeyValue_thenMarksPublished() throws Exception {
        CouponUsedEvent event = CouponUsedEvent.of(
                "coupon-it-1", "promo-it-1", "user-it-1", "order-it-1", 5000L, clock);
        String expectedPayload = objectMapper.writeValueAsString(event);

        writePath.publishCouponUsed(event);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("promotion-it", "true", broker),
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "promotion.coupon.used");

        relay.publishPending();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "promotion.coupon.used", ofSeconds(10));
        assertThat(record.key()).isEqualTo("coupon-it-1");
        assertThat(record.value()).isEqualTo(expectedPayload);
        assertThat(new String(record.headers().lastHeader("eventId").value()))
                .isEqualTo(event.eventId());
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .isEqualTo("CouponUsed");
        consumer.close();

        // publishPending() runs the poll → send → ACK → mark-published loop
        // synchronously, so the row is already drained when it returns.
        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }
}
