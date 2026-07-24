package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
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
 * Renew over HTTP: subscribe → renew the active membership → a new row with a
 * seamless window (validFrom == prior.validTo); renew of an unknown id → 404.
 */
class RenewIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MembershipJpaRepository membershipJpaRepository;

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

    private ResponseEntity<String> renew(String token, String id, String key, String body) {
        return rest.exchange("http://localhost:" + port + "/api/fan/memberships/" + id + "/renew",
                HttpMethod.POST, new HttpEntity<>(body, headers(token, key)), String.class);
    }

    @Test
    @DisplayName("renew an active membership → 201, new row with a stacked window (validFrom = prior.validTo)")
    void renewStacksWindow() throws Exception {
        String token = jwt.signFanToken("fan-" + System.nanoTime());

        ResponseEntity<String> sub = subscribe(token, "sub-1",
                "{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentId\":\"tok_visa_demo\"}");
        assertThat(sub.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode subData = objectMapper.readTree(sub.getBody()).path("data");
        String priorId = subData.path("membershipId").asText();
        String priorValidTo = subData.path("validTo").asText();

        ResponseEntity<String> ren = renew(token, priorId, "ren-1", "{\"planMonths\":1}");
        assertThat(ren.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode renData = objectMapper.readTree(ren.getBody()).path("data");

        assertThat(renData.path("membershipId").asText()).isNotEqualTo(priorId);
        assertThat(renData.path("tier").asText()).isEqualTo("PREMIUM");
        assertThat(renData.path("status").asText()).isEqualTo("ACTIVE");
        // Seamless: the renewed window starts exactly where the prior one ends.
        assertThat(renData.path("validFrom").asText()).isEqualTo(priorValidTo);
        assertThat(membershipJpaRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("renew an unknown membership → 404 MEMBERSHIP_NOT_FOUND")
    void renewUnknown() {
        String token = jwt.signFanToken("fan-" + System.nanoTime());

        ResponseEntity<String> ren = renew(token, "does-not-exist", "ren-x", "{\"planMonths\":1}");

        assertThat(ren.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ren.getBody()).contains("MEMBERSHIP_NOT_FOUND");
    }
}
