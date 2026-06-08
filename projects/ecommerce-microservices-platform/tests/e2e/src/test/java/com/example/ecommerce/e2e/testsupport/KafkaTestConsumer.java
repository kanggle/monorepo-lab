package com.example.ecommerce.e2e.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Host-side consumer that reads a topic from the beginning into a list — used to
 * assert the REAL forward contract event ({@code ecommerce.fulfillment.requested.v1})
 * that shipping-service emits and that the wms outbound-service would consume
 * (TASK-MONO-195). A fresh random group id + {@code auto-offset-reset=earliest}
 * means it always sees events published before it subscribed.
 */
public class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaTestConsumer(String bootstrapServers, String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-assert-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(List.of(topic));
    }

    /**
     * Polls up to {@code timeout} for the first record whose JSON envelope matches
     * the predicate (e.g. payload.orderNo == orderId). Returns the parsed JSON, or
     * {@code null} if none arrives within the window.
     */
    public JsonNode pollFor(java.util.function.Predicate<JsonNode> match, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    JsonNode node = mapper.readTree(record.value());
                    if (match.test(node)) {
                        return node;
                    }
                } catch (Exception ignored) {
                    // not JSON we recognise — skip
                }
            }
        }
        return null;
    }

    /** Drains all currently-available records (diagnostic helper). */
    public List<JsonNode> drain(Duration timeout) {
        List<JsonNode> out = new ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                try {
                    out.add(mapper.readTree(record.value()));
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        return out;
    }

    @Override
    public void close() {
        consumer.close();
    }
}
