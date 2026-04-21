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
 * a unique consumer group so tests are independent.
 * <p>
 * <strong>Offset strategy:</strong> {@code AUTO_OFFSET_RESET=latest} so the
 * consumer only sees records <em>published after the constructor returns</em>.
 * This is critical for test isolation: the Spring context (and therefore the
 * Kafka broker) is cached across test classes in the same JVM, so events
 * produced by earlier tests accumulate on shared topics such as
 * {@code wms.master.warehouse.v1}. An {@code earliest} reset would cause a
 * new consumer to replay every historical event and fail the next
 * {@code aggregateId} assertion (TASK-BE-017). The constructor blocks until
 * the initial partition assignment has landed — i.e., the consumer is
 * committed to {@code endOffsets()} — so that a subsequent producer write
 * is guaranteed to be delivered.
 * <p>
 * <strong>Callers that want to peek at historical records</strong> (e.g., the
 * publisher-resilience test that asserts a drain after unpausing Kafka) must
 * construct the consumer <em>before</em> the producer write, while the broker
 * is still reachable — mirroring the
 * {@code gateway-master-e2e} drain-accumulation pattern.
 */
public final class KafkaTestConsumer implements AutoCloseable {

    private static final Duration ASSIGNMENT_WAIT = Duration.ofSeconds(5);

    private final KafkaConsumer<String, String> consumer;
    private final String topic;

    public KafkaTestConsumer(String bootstrapServers, String topic) {
        this(bootstrapServers, topic, "integration-test-" + UUID.randomUUID());
    }

    public KafkaTestConsumer(String bootstrapServers, String topic, String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // See the class-level javadoc for the rationale behind `latest` — it
        // is required to avoid cross-test pollution from the shared broker.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        this.consumer = new KafkaConsumer<>(props);
        this.topic = topic;
        this.consumer.subscribe(Collections.singletonList(topic));
        waitForPartitionAssignment();
    }

    /**
     * Subscribe() is lazy — the consumer only learns its partitions on the
     * first successful poll. Block until assignment has actually landed so the
     * producer-write that the test is about to trigger is guaranteed to be
     * captured (no race between the POST and the background rebalance).
     *
     * <p>If assignment does not complete within {@link #ASSIGNMENT_WAIT}, we
     * fall back without throwing — the subsequent {@code pollOne} will produce
     * the actual diagnostic ({@link NoSuchElementException} with the topic
     * name).
     */
    private void waitForPartitionAssignment() {
        long deadline = System.nanoTime() + ASSIGNMENT_WAIT.toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(200));
        }
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
