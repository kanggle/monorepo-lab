package com.wms.inventory.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test: a poison record on {@code wms.master.location.v1} must
 * land on {@code wms.master.location.v1.DLT} after the configured error-handler
 * pipeline runs. Mirrors {@link MasterLocationConsumerIntegrationTest} for
 * Testcontainers wiring (Postgres + Kafka + Redis).
 *
 * <p>The {@code MasterEventParser} throws {@link IllegalArgumentException} for
 * malformed JSON, and {@code KafkaConsumerConfig} marks that exception
 * non-retryable. The record therefore flows straight to the DLT without
 * burning the 3-retry budget — which is the documented behaviour for
 * envelope-parse failures (see {@code idempotency.md} §2.5: "Permanent failures
 * (unparseable JSON, unknown eventType): go to DLT immediately"). This test
 * asserts the routing happens; the exponential-backoff retry path for
 * <em>transient</em> failures is exercised separately via {@code @RetryableTopic}
 * unit tests.
 */
class MasterLocationDltRoutingIntegrationTest extends InventoryServiceIntegrationBase {

    private static final String SOURCE_TOPIC = "wms.master.location.v1";

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${inventory.kafka.dlt-suffix:.DLT}")
    private String dltSuffix;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> dltConsumer;

    @AfterEach
    void cleanup() {
        if (producer != null) {
            producer.close();
        }
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        // Reset state so other integration tests aren't affected.
        jdbc.update("DELETE FROM inventory_event_dedupe");
        jdbc.update("DELETE FROM location_snapshot");
    }

    @Test
    @DisplayName("malformed payload on wms.master.location.v1 lands on .DLT with diagnostic headers")
    void poisonRecordLandsOnDlt() throws Exception {
        String dltTopic = SOURCE_TOPIC + dltSuffix;
        String correlationKey = "poison-" + UUID.randomUUID();
        String poisonPayload = "{\"this is not\": \"a valid envelope\""; // trailing garbage → JsonProcessingException

        startDltConsumer(dltTopic);
        publish(SOURCE_TOPIC, correlationKey, poisonPayload);

        AtomicReference<ConsumerRecord<String, String>> received = new AtomicReference<>();
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = dltConsumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> r : records) {
                        if (correlationKey.equals(r.key())) {
                            received.set(r);
                            return;
                        }
                    }
                    assertThat(received.get())
                            .as("DLT record with key %s on topic %s", correlationKey, dltTopic)
                            .isNotNull();
                });

        ConsumerRecord<String, String> dltRecord = received.get();
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.topic()).isEqualTo(dltTopic);
        assertThat(dltRecord.value()).isEqualTo(poisonPayload);

        // Spring Kafka's DeadLetterPublishingRecoverer attaches diagnostic headers
        // describing the original record and the exception that caused the routing.
        Headers headers = dltRecord.headers();
        assertHeaderPresent(headers, "kafka_dlt-original-topic", SOURCE_TOPIC);
        assertHeaderExists(headers, "kafka_dlt-original-partition");
        assertHeaderExists(headers, "kafka_dlt-original-offset");
        assertHeaderExists(headers, "kafka_dlt-exception-fqcn");
        assertHeaderExists(headers, "kafka_dlt-exception-message");

        // Sanity-check: the exception class is the one MasterEventParser throws
        // (wrapped by Spring Kafka — may surface as ListenerExecutionFailedException
        // with IllegalArgumentException as the cause, depending on Spring Kafka version).
        String exceptionFqcn = headerValue(headers, "kafka_dlt-exception-fqcn");
        assertThat(exceptionFqcn)
                .as("DLT exception class header")
                .containsAnyOf(
                        "IllegalArgumentException",
                        "ListenerExecutionFailedException",
                        "MessageConversionException");
    }

    private void publish(String topic, String key, String value) throws Exception {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        producer.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
        producer.flush();
    }

    private void startDltConsumer(String dltTopic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        dltConsumer = new KafkaConsumer<>(props);
        dltConsumer.subscribe(List.of(dltTopic));
    }

    private static void assertHeaderPresent(Headers headers, String name, String expectedValue) {
        Header header = headers.lastHeader(name);
        assertThat(header).as("header %s present", name).isNotNull();
        String actual = new String(header.value(), StandardCharsets.UTF_8);
        assertThat(actual).as("header %s value", name).isEqualTo(expectedValue);
    }

    private static void assertHeaderExists(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        assertThat(header).as("header %s present", name).isNotNull();
        assertThat(header.value()).as("header %s value", name).isNotEmpty();
    }

    private static String headerValue(Headers headers, String name) {
        Header header = headers.lastHeader(name);
        if (header == null) {
            return "";
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
