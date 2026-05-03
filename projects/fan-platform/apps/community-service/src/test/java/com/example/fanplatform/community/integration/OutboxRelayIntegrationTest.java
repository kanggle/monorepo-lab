package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.infrastructure.jpa.PostJpaRepository;
import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Outbox relay integration test (TASK-FAN-BE-002 § Tests § Integration —
 * {@code OutboxRelayIntegrationTest}).
 *
 * <p>Verifies the end-to-end transactional-outbox flow:
 * <ol>
 *   <li>HTTP POST publishes a post (writes a {@code outbox} row, status=PENDING);</li>
 *   <li>{@code CommunityOutboxPollingScheduler} polls within 10s and forwards
 *       the row to Kafka topic {@code community.post.published.v1} with the
 *       standard envelope ({@code eventId, eventType, source, occurredAt,
 *       schemaVersion, partitionKey, payload});</li>
 *   <li>after publishing, the outbox row's {@code published_at} is non-null
 *       and {@code status=PUBLISHED}.</li>
 * </ol>
 *
 * <p>Polling is enabled for this test via {@code @TestPropertySource}; the
 * shared base disables it by default to keep test contexts deterministic.
 */
@TestPropertySource(properties = {
        "outbox.polling.enabled=true",
        "outbox.polling.interval-ms=200"
})
class OutboxRelayIntegrationTest extends CommunityServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    OutboxJpaRepository outboxJpaRepository;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        outboxJpaRepository.deleteAll();
        postJpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        outboxJpaRepository.deleteAll();
        postJpaRepository.deleteAll();
    }

    private HttpHeaders authHeaders(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private KafkaConsumer<String, String> createConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,
                "test-relay-" + UUID.randomUUID().toString().substring(0, 20));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private static List<ConsumerRecord<String, String>> drain(KafkaConsumer<String, String> consumer) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        for (ConsumerRecord<String, String> record : records) {
            out.add(record);
        }
        return out;
    }

    @Test
    @DisplayName("publish post → outbox enqueued → relay forwards to Kafka with envelope")
    void publishPost_relaysToKafka() throws Exception {
        String artistId = "artist-" + UUID.randomUUID().toString().substring(0, 8);
        String artistToken = jwt.signArtistToken(artistId);

        try (KafkaConsumer<String, String> consumer = createConsumer("community.post.published.v1")) {
            String body = """
                    {"postType":"ARTIST_POST","visibility":"PUBLIC","title":"relay","body":"go go"}
                    """;
            ResponseEntity<String> res = rest.exchange(
                    url("/api/community/posts"),
                    HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(artistToken)),
                    String.class);
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode published = objectMapper.readTree(res.getBody());
            String postId = published.path("data").path("postId").asText();
            assertThat(postId).isNotBlank();

            // Wait for the relay to forward to Kafka with the standard envelope.
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                List<ConsumerRecord<String, String>> records = drain(consumer);
                List<ConsumerRecord<String, String>> matching = records.stream()
                        .filter(r -> r.key() != null && r.key().equals(postId))
                        .toList();
                assertThat(matching).isNotEmpty();

                JsonNode envelope = objectMapper.readTree(matching.get(0).value());
                assertThat(envelope.path("eventId").asText()).isNotBlank();
                assertThat(envelope.path("eventType").asText()).isEqualTo("community.post.published");
                assertThat(envelope.path("source").asText()).isEqualTo("fan-platform-community-service");
                assertThat(envelope.path("partitionKey").asText()).isEqualTo(postId);
                assertThat(envelope.path("occurredAt").asText()).isNotBlank();
                assertThat(envelope.has("payload")).isTrue();

                JsonNode payload = envelope.path("payload");
                assertThat(payload.path("postId").asText()).isEqualTo(postId);
                assertThat(payload.path("tenantId").asText()).isEqualTo("fan-platform");
                assertThat(payload.path("authorAccountId").asText()).isEqualTo(artistId);
            });

            // After Kafka publish, the outbox row must be marked PUBLISHED with
            // a non-null published_at.
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                List<OutboxJpaEntity> rows = outboxJpaRepository.findAll().stream()
                        .filter(e -> postId.equals(e.getAggregateId()))
                        .filter(e -> "community.post.published".equals(e.getEventType()))
                        .toList();
                assertThat(rows).isNotEmpty();
                OutboxJpaEntity entity = rows.get(0);
                assertThat(entity.getStatus()).isEqualTo("PUBLISHED");
                assertThat(entity.getPublishedAt()).isNotNull();
            });
        }
    }
}
