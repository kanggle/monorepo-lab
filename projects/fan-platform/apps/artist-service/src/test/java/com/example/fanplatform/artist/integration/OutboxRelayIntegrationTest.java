package com.example.fanplatform.artist.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that registering an artist results in an
 * {@code artist.registered.v1} Kafka message via the outbox relay.
 */
@TestPropertySource(properties = {
        "outbox.polling.enabled=true",
        "outbox.polling.interval-ms=200"
})
class OutboxRelayIntegrationTest extends ArtistServiceIntegrationBase {

    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper objectMapper;

    /**
     * Use a unique stage name per test run so the awaitility filter can
     * exclude {@code artist.registered.v1} envelopes left over from other
     * tests sharing the same broker. Without the filter, an earlier test's
     * payload could capture before the relay publishes ours and the
     * follow-up assertions would fail (the historic flake).
     */
    private static final String UNIQUE_STAGE_NAME = "OutboxTest-" + System.nanoTime();

    @Test
    @DisplayName("register artist → artist.registered.v1 published to Kafka")
    void registeredEventPublished() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt.signAdminToken("admin-1"));
        String body = """
                {"artistType":"SOLO","stageName":"%s"}
                """.formatted(UNIQUE_STAGE_NAME);

        ResponseEntity<String> resp = rest.exchange(
                "/api/artists", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Subscribe to the topic and await the event.
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "artist-test-" + System.nanoTime());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        Properties props = new Properties();
        props.putAll(consumerProps);

        AtomicReference<JsonNode> capturedPayload = new AtomicReference<>();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of("artist.registered.v1"));
            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofMillis(500))
                    .until(() -> {
                        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                        if (records.isEmpty()) return false;
                        for (var record : records) {
                            JsonNode env = objectMapper.readTree(record.value());
                            // Filter on the unique stage name we just registered. Earlier tests
                            // (or earlier runs reusing the broker) may also have published
                            // artist.registered.v1 events; without this guard the wrong payload
                            // would capture and the assertion below would race-fail.
                            if ("artist.registered".equals(env.path("eventType").asText())
                                    && UNIQUE_STAGE_NAME.equals(env.path("payload").path("stageName").asText())) {
                                capturedPayload.set(env);
                                return true;
                            }
                        }
                        return false;
                    });
        }

        JsonNode env = capturedPayload.get();
        assertThat(env).isNotNull();
        assertThat(env.path("eventType").asText()).isEqualTo("artist.registered");
        assertThat(env.path("payload").path("stageName").asText()).isEqualTo(UNIQUE_STAGE_NAME);
        assertThat(env.path("payload").path("tenantId").asText()).isEqualTo("fan-platform");
    }
}
