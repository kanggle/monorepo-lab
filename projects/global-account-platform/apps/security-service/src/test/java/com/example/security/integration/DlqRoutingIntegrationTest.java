package com.example.security.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.security.infrastructure.kafka.KafkaConsumerConfig;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying that the real {@code ErrorHandlingDeserializer}
 * path routes poison-pill payloads to {@code <topic>.dlq} after the configured
 * retry budget, while the original listener container remains healthy.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DlqRoutingIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076).
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private KafkaListenerEndpointRegistry listenerEndpointRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    private KafkaTemplate<String, String> stringTemplate;
    private KafkaTemplate<String, byte[]> byteTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> stringProps = new HashMap<>();
        stringProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        stringProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        stringProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        stringTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(stringProps));

        Map<String, Object> byteProps = new HashMap<>();
        byteProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        byteProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        byteProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        byteTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(byteProps));
    }

    @Test
    @Order(1)
    @DisplayName("Malformed JSON payload triggers DeserializationException and lands on .dlq")
    void malformedJsonRoutedToDlq() {
        String malformedEvent = "THIS IS NOT VALID JSON {{{";
        assertDlqContainsValue("auth.login.succeeded",
                "acc-dlq-001", malformedEvent, malformedEvent);
        assertAllContainersStillRunning();
    }

    @Test
    @Order(4)
    @DisplayName("TASK-BE-248 Phase 2a: auth event missing tenant_id is routed to DLQ and increments outbox.dlq.size")
    void missingTenantIdRoutedToDlqAndMetricIncremented() {
        // Valid JSON but no tenantId in envelope or payload — triggers MissingTenantIdException
        String noTenantEvent = """
                {"eventId":"evt-no-tenant-001","eventType":"auth.login.succeeded",
                 "occurredAt":"2026-05-01T10:00:00Z",
                 "payload":{"accountId":"acc-dlq-004","outcome":"SUCCESS",
                   "ipMasked":"1.2.3.***","timestamp":"2026-05-01T10:00:00Z"}}
                """;
        String topic = "auth.login.succeeded";

        // Capture counter value before sending
        double beforeCount = getDlqSizeCount("tenant_id_missing");

        stringTemplate.send(new ProducerRecord<>(topic, "acc-dlq-004", noTenantEvent));

        // Wait for DLQ arrival
        try (KafkaConsumer<String, String> dlqConsumer = newStringDlqConsumer()) {
            dlqConsumer.subscribe(List.of(topic + ".dlq"));
            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, String>> dlqRecords = new ArrayList<>();
                records.forEach(dlqRecords::add);
                assertThat(dlqRecords).as("missing tenant_id event must reach .dlq").isNotEmpty();
            });
        }

        // outbox.dlq.size{reason=tenant_id_missing} must have incremented
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            double afterCount = getDlqSizeCount("tenant_id_missing");
            assertThat(afterCount).as("outbox.dlq.size counter must increment").isGreaterThan(beforeCount);
        });

        assertAllContainersStillRunning();
    }

    private double getDlqSizeCount(String reason) {
        try {
            Counter counter = meterRegistry.get(KafkaConsumerConfig.DLQ_SIZE_METRIC)
                    .tag("reason", reason)
                    .counter();
            return counter.count();
        } catch (MeterNotFoundException e) {
            return 0.0;
        }
    }

    @Test
    @Order(3)
    @DisplayName("TASK-BE-041b-fix: account.locked payload missing eventId is routed to account.locked.dlq")
    void accountLockedMissingEventIdRoutedToDlq() {
        // Contract (specs/contracts/events/account-events.md) now mandates eventId on
        // the account.locked payload. AccountLockedConsumer throws
        // IllegalArgumentException when it is absent, and DefaultErrorHandler routes
        // the record to account.locked.dlq after exhausting retries.
        String noEventId = """
                {"accountId":"acc-dlq-003","reasonCode":"ADMIN_LOCK",
                 "actorType":"operator","actorId":"op-1","lockedAt":"2026-04-14T10:00:00Z"}
                """;
        assertDlqContainsValue("account.locked", "acc-dlq-003", noEventId, noEventId);
        assertAllContainersStillRunning();
    }

    @Test
    @Order(2)
    @DisplayName("Invalid UTF-8 / non-JSON bytes are routed to .dlq via ErrorHandlingDeserializer")
    void invalidBytesRoutedToDlq() {
        byte[] poison = new byte[]{(byte) 0xC3, (byte) 0x28, 0x00, 0x1F, (byte) 0xFF};
        String accountId = "acc-dlq-002";
        byteTemplate.send(new ProducerRecord<>("auth.login.failed", accountId, poison));

        try (KafkaConsumer<String, byte[]> dlqConsumer = newByteDlqConsumer()) {
            dlqConsumer.subscribe(List.of("auth.login.failed.dlq"));
            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, byte[]> records = dlqConsumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, byte[]>> collected = new ArrayList<>();
                records.forEach(collected::add);
                assertThat(collected).as("poison bytes routed to .dlq").isNotEmpty();
                assertThat(collected.get(0).value()).isEqualTo(poison);
            });
        }
        assertAllContainersStillRunning();
    }

    private void assertDlqContainsValue(String topic, String key, String sentValue, String expectedDlqValue) {
        stringTemplate.send(new ProducerRecord<>(topic, key, sentValue));

        try (KafkaConsumer<String, String> dlqConsumer = newStringDlqConsumer()) {
            dlqConsumer.subscribe(List.of(topic + ".dlq"));
            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, String>> dlqRecords = new ArrayList<>();
                records.forEach(dlqRecords::add);
                assertThat(dlqRecords).isNotEmpty();
                assertThat(dlqRecords.get(0).value()).isEqualTo(expectedDlqValue);
            });
        }
    }

    private KafkaConsumer<String, String> newStringDlqConsumer() {
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private KafkaConsumer<String, byte[]> newByteDlqConsumer() {
        Map<String, Object> props = baseConsumerProps();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        return new KafkaConsumer<>(props);
    }

    private Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    private void assertAllContainersStillRunning() {
        Collection<MessageListenerContainer> containers = listenerEndpointRegistry.getListenerContainers();
        assertThat(containers).isNotEmpty();
        for (MessageListenerContainer c : containers) {
            assertThat(c.isRunning())
                    .as("listener container %s remains running after poison pill", c.getListenerId())
                    .isTrue();
        }
    }
}
