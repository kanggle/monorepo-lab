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
 * Idempotent subscribe: same key + same payload → one row, identical result;
 * same key + different payload → 409 IDEMPOTENCY_KEY_CONFLICT.
 */
class IdempotentSubscribeIntegrationTest extends MembershipServiceIntegrationBase {

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

    private HttpHeaders headers(String bearer, String key) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        h.set("Idempotency-Key", key);
        return h;
    }

    private ResponseEntity<String> subscribe(String token, String key, String body) {
        return rest.exchange("http://localhost:" + port + "/api/fan/memberships",
                HttpMethod.POST, new HttpEntity<>(body, headers(token, key)), String.class);
    }

    @Test
    @DisplayName("same key + same payload → single row, identical membershipId")
    void replaySameResult() throws Exception {
        String token = jwt.signFanToken("fan-" + System.nanoTime());
        String body = "{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentId\":\"tok_visa_demo\"}";

        ResponseEntity<String> first = subscribe(token, "idem-1", body);
        ResponseEntity<String> second = subscribe(token, "idem-1", body);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();

        String firstId = objectMapper.readTree(first.getBody()).path("data").path("membershipId").asText();
        String secondId = objectMapper.readTree(second.getBody()).path("data").path("membershipId").asText();
        assertThat(secondId).isEqualTo(firstId);
        assertThat(membershipJpaRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("same key + different payload → 409 IDEMPOTENCY_KEY_CONFLICT")
    void conflictDifferentPayload() {
        String token = jwt.signFanToken("fan-" + System.nanoTime());
        subscribe(token, "idem-2", "{\"tier\":\"PREMIUM\",\"planMonths\":1}");

        ResponseEntity<String> conflict = subscribe(token, "idem-2",
                "{\"tier\":\"MEMBERS_ONLY\",\"planMonths\":3}");

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).contains("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(membershipJpaRepository.count()).isEqualTo(1);
    }
}
