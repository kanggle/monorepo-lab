package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.application.billing.AutoRenewMembershipsUseCase;
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

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Billing-key enrollment over HTTP + encryption-at-rest + the auto-renew chain
 * (TASK-FAN-BE-033 / ADR-002). Proves: the issuance/cancel endpoints and their
 * flat contract; the key is encrypted in {@code billing_key_encrypted} (never the
 * plaintext, never in the response); and one auto-renew tick charges the stored key
 * (mock) and drives the unchanged {@code RenewMembershipUseCase} to create a
 * seamless renewal — then is idempotent (no double renew).
 */
class BillingKeyEnrollmentIntegrationTest extends MembershipServiceIntegrationBase {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    MembershipJpaRepository membershipJpaRepository;

    @Autowired
    AutoRenewMembershipsUseCase autoRenewUseCase;

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

    private HttpHeaders auth(String bearer) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(bearer);
        return h;
    }

    private HttpHeaders authIdem(String bearer, String key) {
        HttpHeaders h = auth(bearer);
        h.set("Idempotency-Key", key);
        return h;
    }

    @Test
    @DisplayName("enroll → 201 (no key in body), key encrypted at rest; cancel → 200 then 404")
    void enrollEncryptsAndCancel() throws Exception {
        String token = jwt.signFanToken("fan-enc-" + System.nanoTime());
        String plaintextKey = "bk_live_opaque_" + System.nanoTime();

        ResponseEntity<String> enroll = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships/billing-key",
                HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"billingKey\":\"" + plaintextKey + "\"}", auth(token)),
                String.class);

        assertThat(enroll.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(enroll.getBody()).doesNotContain(plaintextKey);
        assertThat(enroll.getBody()).doesNotContain("billingKey");
        JsonNode data = objectMapper.readTree(enroll.getBody());
        assertThat(data.path("tier").asText()).isEqualTo("PREMIUM");
        assertThat(data.path("active").asBoolean()).isTrue();
        assertThat(data.path("enrollmentId").asText()).isNotBlank();

        // At rest: the stored column is an AES-GCM envelope, NOT the plaintext.
        String stored = jdbcTemplate.queryForObject(
                "SELECT billing_key_encrypted FROM billing_key_enrollments WHERE active = true", String.class);
        assertThat(stored).isNotNull().isNotEqualTo(plaintextKey).doesNotContain(plaintextKey);

        // Cancel → 200 active=false, then a second cancel → 404.
        ResponseEntity<String> cancel = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships/billing-key/PREMIUM",
                HttpMethod.DELETE, new HttpEntity<>(auth(token)), String.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(cancel.getBody()).path("active").asBoolean()).isFalse();

        ResponseEntity<String> cancelAgain = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships/billing-key/PREMIUM",
                HttpMethod.DELETE, new HttpEntity<>(auth(token)), String.class);
        assertThat(cancelAgain.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(cancelAgain.getBody()).contains("BILLING_KEY_ENROLLMENT_NOT_FOUND");
    }

    @Test
    @DisplayName("auto-renew tick charges the stored key and drives RenewMembershipUseCase → +1 row, idempotent")
    void autoRenewDrivesRenew() throws Exception {
        String sub = "fan-renew-" + System.nanoTime();
        String token = jwt.signFanToken(sub);

        // Subscribe a PREMIUM membership (mock PG approves).
        ResponseEntity<String> subscribe = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships",
                HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentId\":\"tok_visa_demo\"}",
                        authIdem(token, "sub-ar")),
                String.class);
        assertThat(subscribe.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String membershipId = objectMapper.readTree(subscribe.getBody()).path("data").path("membershipId").asText();

        // Make it DUE: pull valid_to into the 1-day look-ahead window (still in-window / ACTIVE).
        jdbcTemplate.update("UPDATE memberships SET valid_to = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(3600)), membershipId);

        // Enroll a billing key for the tier.
        ResponseEntity<String> enroll = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships/billing-key",
                HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"billingKey\":\"bk_auto_" + System.nanoTime() + "\"}", auth(token)),
                String.class);
        assertThat(enroll.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(membershipJpaRepository.count()).isEqualTo(1);

        int renewed = autoRenewUseCase.runOnce(100);
        assertThat(renewed).isEqualTo(1);
        assertThat(membershipJpaRepository.count()).isEqualTo(2);

        // Idempotent: the renewed row's validTo is now far in the future, so it is not re-selected.
        int renewedAgain = autoRenewUseCase.runOnce(100);
        assertThat(renewedAgain).isZero();
        assertThat(membershipJpaRepository.count()).isEqualTo(2);
    }
}
