package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
import com.example.messaging.outbox.OutboxJpaRepository;
import org.awaitility.Awaitility;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outbox relay: subscribe → outbox row → Kafka publish → {@code published_at}
 * set. The poller is enabled for this class via @TestPropertySource.
 */
@TestPropertySource(properties = {
        "outbox.polling.enabled=true",
        "outbox.polling.interval-ms=200"
})
class OutboxRelayIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MembershipJpaRepository membershipJpaRepository;

    @Autowired
    OutboxJpaRepository outboxJpaRepository;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    @Test
    @DisplayName("subscribe → activated outbox row drained to Kafka → published_at set")
    void relayDrainsToKafka() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(jwt.signFanToken("fan-" + System.nanoTime()));
        h.set("Idempotency-Key", "relay-key");

        rest.exchange("http://localhost:" + port + "/api/fan/memberships", HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"planMonths\":1}", h), String.class);

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(250))
                .untilAsserted(() -> assertThat(outboxJpaRepository.findAll())
                        .isNotEmpty()
                        .allMatch(e -> e.getPublishedAt() != null
                                && "PUBLISHED".equals(e.getStatus())));
    }
}
