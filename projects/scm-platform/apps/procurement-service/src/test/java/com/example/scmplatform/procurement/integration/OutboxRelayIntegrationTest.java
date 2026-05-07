package com.example.scmplatform.procurement.integration;

import com.example.messaging.outbox.OutboxJpaRepository;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * IT-2: Outbox relay — end-to-end verification.
 *
 * <p>Flow: PO submit → outbox_events row insert (same transaction) →
 * {@link ProcurementOutboxPollingScheduler} polling loop → Kafka topic
 * {@code scm.procurement.po.submitted.v1} → Kafka consumer receives the message.
 *
 * <p>The outbox polling scheduler is enabled for this test class via
 * {@code @TestPropertySource}. The {@link ProcurementOutboxPollingScheduler}
 * bean is {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}
 * on {@code outbox.polling.enabled=true}.
 *
 * <p>The supplier HTTP endpoint is stubbed with OkHttp MockWebServer so that
 * {@link com.example.scmplatform.procurement.infrastructure.supplier.RestSupplierAdapter}
 * returns a successful receipt reference without a real supplier service.
 */
@Tag("integration")
@DisplayName("IT-2: Outbox relay to Kafka")
@TestPropertySource(properties = {
        "outbox.polling.enabled=true",
        "outbox.polling.interval-ms=200"
})
class OutboxRelayIntegrationTest extends AbstractProcurementIntegrationTest {

    // Per-class unique consumer group prevents cross-test offset accumulation
    // (TASK-MONO-046-3 pattern).
    private static final String CONSUMER_GROUP =
            "it-outbox-relay-" + UUID.randomUUID();

    private static MockWebServer supplierMock;

    @DynamicPropertySource
    static void supplierMockUrl(DynamicPropertyRegistry registry) throws IOException {
        supplierMock = new MockWebServer();
        supplierMock.start();
        // Lazy URL so the port is resolved after the server starts.
        registry.add("scmplatform.procurement.supplier.mock.base-url",
                () -> "http://" + supplierMock.getHostName() + ":" + supplierMock.getPort());
    }

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private OutboxJpaRepository outboxJpa;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // auto-offset-reset=latest: only consume messages produced after subscription
        // so earlier test runs' messages don't bleed into this test (TASK-MONO-046-3).
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        consumer = new KafkaConsumer<>(props);
        // Topic name: ProcurementOutboxPollingScheduler.TOPIC_PO_SUBMITTED (package-private)
        consumer.subscribe(List.of("scm.procurement.po.submitted.v1"));
        // Poll once to trigger partition assignment before producing.
        consumer.poll(Duration.ofMillis(500));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (consumer != null) {
            consumer.close();
        }
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("PO submit → outbox row 생성 → polling scheduler → Kafka topic 수신")
    void submitPoCausesOutboxRelayToKafka() {
        // Arrange: stub supplier to return success
        supplierMock.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"receiptRef\":\"RCPT-IT-001\",\"status\":\"RECEIVED\"}"));

        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());

        ActorContext buyer = new ActorContext("buyer-relay-001", TENANT_SCM, Set.of("BUYER"));

        // Act: submit PO — this writes to outbox within the same transaction
        service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(), "idem-relay-001"));

        // Assert: outbox row inserted
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long pending = outboxJpa.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(po.getId()))
                    .count();
            assertThat(pending).isGreaterThanOrEqualTo(1);
        });

        // Assert: Kafka message received (scheduler relays it within polling interval)
        List<String> received = new ArrayList<>();
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(r -> received.add(r.value()));
            assertThat(received).anyMatch(v -> v.contains(po.getId()));
        });
    }
}
