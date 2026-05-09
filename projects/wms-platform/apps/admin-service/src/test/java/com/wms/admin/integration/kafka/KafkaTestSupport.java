package com.wms.admin.integration.kafka;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Test utility for the projection Kafka IT classes.
 *
 * <ul>
 *   <li>{@link #envelope} — build a canonical projection envelope JSON
 *       string for a given eventType / eventId / occurredAt / payload.</li>
 *   <li>{@link #pollDlt} — drain records from a {@code <topic>.DLT} topic
 *       within a bounded timeout (used by DLT routing IT).</li>
 * </ul>
 */
final class KafkaTestSupport {

    private KafkaTestSupport() {}

    /** Build a canonical projection envelope JSON for an event. */
    static String envelope(UUID eventId, String eventType, Instant occurredAt,
                           String aggregateId, String payloadJson) {
        return ("""
                {
                  "eventId": "%s",
                  "eventType": "%s",
                  "occurredAt": "%s",
                  "aggregateId": "%s",
                  "payload": %s
                }
                """).formatted(eventId, eventType, occurredAt.toString(), aggregateId, payloadJson);
    }

    /**
     * Subscribe a fresh consumer to the {@code <topic>.DLT} topic and drain
     * records up to {@code timeout}. Returns whatever was received in that
     * window. The caller asserts non-empty / record content.
     */
    static List<ConsumerRecord<String, String>> pollDlt(String bootstrapServers,
                                                        String dltTopic,
                                                        Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-probe-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(dltTopic));
            long deadline = System.nanoTime() + timeout.toNanos();
            java.util.ArrayList<ConsumerRecord<String, String>> all = new java.util.ArrayList<>();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    all.add(r);
                }
                if (!all.isEmpty()) {
                    // Keep polling for a short additional window to catch
                    // late-arriving records, then exit.
                    long shortStop = System.nanoTime() + Duration.ofMillis(500).toNanos();
                    while (System.nanoTime() < shortStop) {
                        ConsumerRecords<String, String> more = consumer.poll(Duration.ofMillis(200));
                        for (ConsumerRecord<String, String> r : more) {
                            all.add(r);
                        }
                    }
                    return all;
                }
            }
            return all;
        }
    }

    /** Convenience: returns whether {@code list} contains a record with eventId in the value. */
    static boolean valueContains(Collection<ConsumerRecord<String, String>> records,
                                 String needle) {
        for (ConsumerRecord<String, String> r : records) {
            if (r.value() != null && r.value().contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
