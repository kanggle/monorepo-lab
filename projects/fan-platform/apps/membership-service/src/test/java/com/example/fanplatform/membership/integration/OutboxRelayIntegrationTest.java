package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
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
 * Outbox relay (TASK-FAN-BE-020, outbox v2): subscribe → {@code membership_outbox}
 * row → Kafka publish → {@code published_at} set. The v2 relay
 * ({@code MembershipOutboxPublisher}) is an unconditional {@code @Component}; the
 * poll/initial-delay are tightened here so it drains within the awaitility window.
 */
@TestPropertySource(properties = {
        "membership.outbox.poll-ms=200",
        "membership.outbox.initial-delay-ms=200"
})
class OutboxRelayIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MembershipJpaRepository membershipJpaRepository;

    @Autowired
    MembershipOutboxJpaRepository outboxJpaRepository;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    @Test
    @DisplayName("subscribe → activated membership_outbox row drained to Kafka → published_at set")
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
                        .allMatch(e -> e.getPublishedAt() != null));
    }
}
