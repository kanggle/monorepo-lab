package com.example.shipping;

import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.infrastructure.event.ShippingOutboxPublisher;
import com.example.shipping.infrastructure.event.ShippingOutboxRepository;
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

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test for the shipping-service outbox v2 relay
 * (TASK-BE-446). Proves: the write path persists a {@code shipping_outbox} row →
 * {@link ShippingOutboxPublisher} drains it to Kafka with the {@code eventId}/{@code eventType}
 * headers + preserved key ({@code shippingId}) → the row's {@code published_at} is set.
 *
 * <p><b>Verification note (TASK-BE-446 AC-7):</b> shipping-service has no
 * Testcontainers CI lane (only order/payment do), so this {@code @Tag("integration")}
 * test is excluded from the Docker-free {@code :test} task and does not run in CI
 * today. Authored for compile-time verification + activation if a lane is added.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "shipping.shipping.status-changed")
@DisplayName("shipping outbox v2 relay 통합 테스트")
class ShippingOutboxRelayIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shipping_db")
            .withUsername("shipping_user")
            .withPassword("shipping_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ShippingEventPublisher writePath;

    @Autowired
    private ShippingOutboxPublisher relay;

    @Autowired
    private ShippingOutboxRepository outboxRepository;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("write path 가 shipping_outbox 행을 적재하고 relay 가 헤더+키 보존하여 발행한다")
    void writeThenRelay_publishesWithHeadersAndPreservedKey_thenMarksPublished() {
        writePath.publishShippingStatusChanged(
                "ecommerce", "ship-it-1", "order-it-1", "user-it-1",
                ShippingStatus.PREPARING, ShippingStatus.SHIPPED, "TRK-IT", "CJ");
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("shipping-it", "true", broker),
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "shipping.shipping.status-changed");

        relay.publishPending();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "shipping.shipping.status-changed", ofSeconds(10));
        assertThat(record.key()).isEqualTo("ship-it-1");
        assertThat(record.value()).contains("\"shippingId\":\"ship-it-1\"");
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .isEqualTo("ShippingStatusChanged");
        assertThat(record.headers().lastHeader("eventId")).isNotNull();
        consumer.close();

        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }
}
