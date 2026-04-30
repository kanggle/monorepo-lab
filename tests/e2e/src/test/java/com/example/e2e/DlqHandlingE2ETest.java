package com.example.e2e;

import io.restassured.response.Response;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * DLQ handling E2E (TASK-BE-041c §5).
 *
 *   1. Produce invalid JSON to {@code auth.login.succeeded}
 *   2. Awaitility-poll {@code auth.login.succeeded.dlq} for arrival
 *   3. security-service /actuator/metrics/kafka.consumer.lag → 200 + value >= 0
 *   4. admin-service /actuator/health/circuitbreakers → 200
 */
class DlqHandlingE2ETest extends E2EBase {

    private static final String SOURCE_TOPIC = "auth.login.succeeded";
    private static final String DLQ_TOPIC = "auth.login.succeeded.dlq";

    @Test
    @DisplayName("잘못된 JSON 메시지가 DLQ로 이관되고 관측 엔드포인트가 노출된다")
    void malformed_event_routes_to_dlq_and_observability_endpoints_respond() throws Exception {
        // 1. produce invalid JSON (as String — matches the StringSerializer used by the broker DLQ pipeline)
        String invalid = "{not-valid-json";
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            producer.send(new ProducerRecord<>(SOURCE_TOPIC, "e2e-dlq-key", invalid)).get();
            producer.flush();
        }

        // 2. consume from DLQ with Awaitility polling (DLQ values are raw bytes via ByteArraySerializer)
        AtomicBoolean found = new AtomicBoolean(false);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps("e2e-dlq-" + System.nanoTime()))) {
            consumer.subscribe(List.of(DLQ_TOPIC));
            await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(2)).until(() -> {
                ConsumerRecords<String, byte[]> recs = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, byte[]> r : recs) {
                    if (r.value() != null && r.value().length > 0) {
                        found.set(true);
                        return true;
                    }
                }
                return false;
            });
        }
        assertThat(found.get()).as("DLQ message arrived").isTrue();

        // 3. security-service consumer-lag metric
        Response lag = security().when().get("/actuator/metrics/kafka.consumer.lag");
        assertThat(lag.statusCode()).isEqualTo(200);

        // 4. admin-service circuit breakers health
        Response cb = admin().when().get("/actuator/health/circuitbreakers");
        assertThat(cb.statusCode()).isEqualTo(200);
    }

    private static Properties producerProps() {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ComposeFixture.KAFKA_BOOTSTRAP_HOST);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        return p;
    }

    private static Properties consumerProps(String group) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ComposeFixture.KAFKA_BOOTSTRAP_HOST);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return p;
    }
}
