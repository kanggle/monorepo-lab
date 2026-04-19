package com.wms.master.integration.support;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Thin wrapper over {@link KafkaConsumer} for integration tests.
 * <p>
 * Subscribes once, polls with a bounded timeout, and exposes {@link
 * #pollOne(Duration)} / {@link #pollAll(Duration)} helpers. Each instance uses
 * a unique consumer group so tests are independent, and {@code
 * AUTO_OFFSET_RESET=earliest} so messages published before subscribe are not
 * lost (important for the publisher-resilience test).
 */
public final class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final String topic;

    public KafkaTestConsumer(String bootstrapServers, String topic) {
        this(bootstrapServers, topic, "integration-test-" + UUID.randomUUID());
    }

    public KafkaTestConsumer(String bootstrapServers, String topic, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        this.consumer = new KafkaConsumer<>(props);
        this.topic = topic;
        this.consumer.subscribe(Collections.singletonList(topic));
        // Prime the subscription so earliest offset is committed at consumer start
        this.consumer.poll(Duration.ofMillis(100));
    }

    /**
     * Blocks up to {@code timeout} for exactly one record on the subscribed
     * topic. Throws {@link NoSuchElementException} if none arrives.
     */
    public ConsumerRecord<String, String> pollOne(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new NoSuchElementException(
                "No record received on topic '" + topic + "' within " + timeout);
    }

    /**
     * Polls for up to {@code timeout}, returning all records seen. Useful when
     * a single commit produces multiple events (updated + deactivated).
     */
    public List<ConsumerRecord<String, String>> pollAll(Duration timeout) {
        java.util.List<ConsumerRecord<String, String>> out = new java.util.ArrayList<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(out::add);
        }
        return out;
    }

    public Map<String, String> headerMap(ConsumerRecord<String, String> record) {
        Map<String, String> map = new HashMap<>();
        record.headers().forEach(h -> map.put(h.key(),
                h.value() == null ? null : new String(h.value())));
        return map;
    }

    @Override
    public void close() {
        try {
            consumer.close(Duration.ofSeconds(5));
        } catch (Exception e) {
            // best-effort on test teardown
        }
    }

    public static void sleep(Duration d) {
        try {
            TimeUnit.MILLISECONDS.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
