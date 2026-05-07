package com.example.scmplatform.e2e.testsupport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Minimal {@link KafkaConsumer} wrapper used by the e2e suite to peek at
 * events emitted by procurement-service via the outbox pattern, plus the
 * wms-platform cross-project events fed by {@link KafkaTestProducer}.
 *
 * <p>Spins up a background poller thread so tests simply
 * {@code drain()} accumulated records inside an Awaitility loop until the
 * payload assertion passes (or the timeout elapses).
 *
 * <p>Each instance subscribes with a unique consumer group id so concurrent
 * scenarios do not contend for partition ownership of the same topic.
 *
 * <p>Adapted from {@code projects/fan-platform/tests/e2e/.../KafkaTestConsumer}.
 */
public final class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final Thread pollThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ConcurrentLinkedQueue<ConsumerRecord<String, String>> buffer =
            new ConcurrentLinkedQueue<>();

    public KafkaTestConsumer(String bootstrapServers, Collection<String> topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "scm-e2e-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(topics);

        this.pollThread = new Thread(this::pollLoop, "kafka-scm-e2e-consumer");
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                records.forEach(buffer::add);
            } catch (org.apache.kafka.common.errors.WakeupException wakeup) {
                return;
            } catch (RuntimeException ex) {
                if (!running.get()) {
                    return;
                }
                // Transient broker errors — let the next poll retry. Tests assert
                // via drain() inside Awaitility, so a couple lost polls do not
                // mask a real failure (the assertion eventually fails).
            }
        }
    }

    /** Returns all records buffered so far, clearing the buffer. */
    public List<ConsumerRecord<String, String>> drain() {
        List<ConsumerRecord<String, String>> snapshot = new ArrayList<>();
        ConsumerRecord<String, String> head;
        while ((head = buffer.poll()) != null) {
            snapshot.add(head);
        }
        return Collections.unmodifiableList(snapshot);
    }

    @Override
    public void close() {
        running.set(false);
        try {
            consumer.wakeup();
        } catch (RuntimeException ignored) {
            // already closed in an error path
        }
        try {
            pollThread.join(Duration.ofSeconds(5).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        consumer.close(Duration.ofSeconds(5));
    }
}
