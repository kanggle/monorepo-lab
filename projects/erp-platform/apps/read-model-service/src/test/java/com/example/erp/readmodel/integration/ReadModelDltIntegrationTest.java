package com.example.erp.readmodel.integration;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Poison-envelope → DLT integration test (AC-3): an invalid envelope (null
 * eventId) is routed straight to the {@code <topic>.DLT} without retry, and the
 * projection is never mutated.
 */
class ReadModelDltIntegrationTest extends AbstractReadModelIntegrationTest {

    @Test
    void invalidEnvelopeRoutesToDlt() {
        // Null eventId → InvalidEnvelopeException → immediate DLT (no retry).
        String poison = "{ \"eventId\": null, \"aggregateType\": \"employee\","
                + " \"aggregateId\": \"emp-poison\","
                + " \"payload\": { \"changeKind\": \"CREATED\", \"after\": {} } }";
        publish(TOPIC_EMPLOYEE, "emp-poison", poison);

        // The .DLT topic for the employee topic should receive the poison record.
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            boolean found = pollDltContains(TOPIC_EMPLOYEE + ".DLT");
            assertThat(found).as("poison record reached DLT").isTrue();
        });
        // No projection row created from the poison message.
        assertThat(employeeJpa.findById("emp-poison")).isEmpty();
    }

    private boolean pollDltContains(String dltTopic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-verify-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Pattern.compile(Pattern.quote(dltTopic)));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, String> r : records) {
                if (r.value() != null) {
                    return true;
                }
            }
            // Topic may not yet exist (subscribe-by-pattern needs a metadata
            // refresh); Awaitility retries the whole poll.
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
