package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A persistent / non-retryable failure (unsupported {@code schemaVersion}) is
 * routed to {@code <topic>.dlq} with the original value, no notification is
 * created, and the listener container keeps running — no partition stall
 * (architecture.md § Retry and DLQ, AC-3).
 */
class DlqRoutingIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private NotificationJpaRepository notifications;

    @BeforeEach
    void setUp() {
        truncateAll();
        awaitListenersAssigned();
    }

    @Test
    @DisplayName("unsupported schemaVersion → fan.membership.activated.v1.dlq, no notification, container alive")
    void unsupportedSchemaRoutedToDlq() {
        String poison = activatedEnvelope("evt-poison-1", "mem-1", "acc-1", "PREMIUM")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":99");

        producer().send(TOPIC_ACTIVATED, "mem-1", poison);

        try (KafkaConsumer<String, String> dlqConsumer = newDlqConsumer()) {
            dlqConsumer.subscribe(List.of(TOPIC_ACTIVATED + ".dlq"));
            await().atMost(60, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = dlqConsumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, String>> collected = new ArrayList<>();
                records.forEach(collected::add);
                assertThat(collected).as("poison event must reach the .dlq topic").isNotEmpty();
                assertThat(collected.get(0).value()).contains("evt-poison-1");
            });
        }

        // No notification was created from the poison event.
        assertThat(notifications.count()).isZero();
        // The listener containers remain running (no partition stall).
        for (MessageListenerContainer c : listenerRegistry.getListenerContainers()) {
            assertThat(c.isRunning())
                    .as("listener container %s stays running after the poison pill", c.getListenerId())
                    .isTrue();
        }
    }

    private KafkaConsumer<String, String> newDlqConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
