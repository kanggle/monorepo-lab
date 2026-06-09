package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
import com.example.messaging.outbox.OutboxJpaRepository;
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
 * tok_decline → 422 PAYMENT_DECLINED, NO membership row, NO outbox event.
 */
class PaymentDeclineIntegrationTest extends MembershipServiceIntegrationBase {

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
    @DisplayName("tok_decline → 422 PAYMENT_DECLINED + no row + no event")
    void declineCreatesNothing() {
        String fanToken = jwt.signFanToken("fan-" + System.nanoTime());
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(fanToken);
        h.set("Idempotency-Key", "decline-key");

        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships", HttpMethod.POST,
                new HttpEntity<>("{\"tier\":\"PREMIUM\",\"planMonths\":1,\"paymentToken\":\"tok_decline\"}", h),
                String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody()).contains("PAYMENT_DECLINED");
        assertThat(membershipJpaRepository.count()).isZero();
        assertThat(outboxJpaRepository.count()).isZero();
    }
}
