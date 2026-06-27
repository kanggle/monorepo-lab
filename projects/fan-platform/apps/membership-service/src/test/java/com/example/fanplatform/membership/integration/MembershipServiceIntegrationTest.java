package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Happy-path E2E: subscribe (approve) → ACTIVE → cancel. Asserts persistence,
 * the read-time {@code active} flag, the DB-roundtrip timestamp equality (§15),
 * and outbox enqueue.
 */
class MembershipServiceIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MembershipJpaRepository membershipJpaRepository;

    @Autowired
    MembershipOutboxJpaRepository outboxJpaRepository;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        truncateAll();
    }

    @AfterEach
    void cleanUp() {
        truncateAll();
    }

    private HttpHeaders headers(String bearer, String idempotencyKey) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        if (idempotencyKey != null) {
            h.set("Idempotency-Key", idempotencyKey);
        }
        return h;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @DisplayName("subscribe → ACTIVE (+activated outbox) → cancel → CANCELED (+canceled outbox)")
    void subscribeThenCancel() throws Exception {
        String fanId = "fan-" + System.nanoTime();
        String fanToken = jwt.signFanToken(fanId);

        // subscribe
        ResponseEntity<String> subRes = rest.exchange(
                url("/api/fan/memberships"), HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentToken\":\"tok_visa_demo\"}",
                        headers(fanToken, "key-1")),
                String.class);
        assertThat(subRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode subJson = objectMapper.readTree(subRes.getBody());
        assertThat(subJson.has("data")).isTrue();
        assertThat(subJson.has("meta")).isTrue();
        String membershipId = subJson.path("data").path("membershipId").asText();
        assertThat(membershipId).isNotBlank();
        assertThat(subJson.path("data").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(subJson.path("data").path("active").asBoolean()).isTrue();
        assertThat(subJson.path("data").path("paymentRef").asText()).startsWith("pgmock_");

        // §15 — response validFrom equals the DB re-read (micros round-trip).
        var stored = membershipJpaRepository.findById(membershipId).orElseThrow();
        assertThat(subJson.path("data").path("validFrom").asText())
                .isEqualTo(stored.getValidFrom().toString());

        // activated outbox row
        assertThat(outboxJpaRepository.findAll())
                .anyMatch(e -> "fan.membership.activated".equals(e.getEventType())
                        && membershipId.equals(e.getAggregateId()));

        // cancel
        ResponseEntity<String> cancelRes = rest.exchange(
                url("/api/fan/memberships/" + membershipId + "/cancel"), HttpMethod.POST,
                new HttpEntity<>("{\"reason\":\"done\"}", headers(fanToken, null)),
                String.class);
        assertThat(cancelRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cancelJson = objectMapper.readTree(cancelRes.getBody());
        assertThat(cancelJson.path("data").path("status").asText()).isEqualTo("CANCELED");
        assertThat(cancelJson.path("data").path("canceledAt").isNull()).isFalse();

        // canceled outbox row
        assertThat(outboxJpaRepository.findAll())
                .anyMatch(e -> "fan.membership.canceled".equals(e.getEventType())
                        && membershipId.equals(e.getAggregateId()));
    }

    @Test
    @DisplayName("re-cancel of CANCELED → idempotent 200 no-op, NO new canceled outbox event")
    void recancelIdempotent() {
        String fanId = "fan-" + System.nanoTime();
        String fanToken = jwt.signFanToken(fanId);

        ResponseEntity<String> subRes = rest.exchange(
                url("/api/fan/memberships"), HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"planMonths\":1}", headers(fanToken, "key-2")),
                String.class);
        String membershipId = subRes.getBody() == null ? null
                : subRes.getBody().replaceAll(".*\"membershipId\":\"([^\"]+)\".*", "$1");

        rest.exchange(url("/api/fan/memberships/" + membershipId + "/cancel"), HttpMethod.POST,
                new HttpEntity<>("{}", headers(fanToken, null)), String.class);
        long canceledEventsAfterFirst = outboxJpaRepository.findAll().stream()
                .filter(e -> "fan.membership.canceled".equals(e.getEventType())).count();

        ResponseEntity<String> recancel = rest.exchange(
                url("/api/fan/memberships/" + membershipId + "/cancel"), HttpMethod.POST,
                new HttpEntity<>("{}", headers(fanToken, null)), String.class);
        assertThat(recancel.getStatusCode()).isEqualTo(HttpStatus.OK);

        long canceledEventsAfterSecond = outboxJpaRepository.findAll().stream()
                .filter(e -> "fan.membership.canceled".equals(e.getEventType())).count();
        assertThat(canceledEventsAfterSecond).isEqualTo(canceledEventsAfterFirst);
    }

    @Test
    @DisplayName("cancel unknown id → 404 MEMBERSHIP_NOT_FOUND")
    void cancelUnknown() {
        String fanToken = jwt.signFanToken("fan-" + System.nanoTime());
        ResponseEntity<String> res = rest.exchange(
                url("/api/fan/memberships/does-not-exist/cancel"), HttpMethod.POST,
                new HttpEntity<>("{}", headers(fanToken, null)), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("MEMBERSHIP_NOT_FOUND");
    }
}
