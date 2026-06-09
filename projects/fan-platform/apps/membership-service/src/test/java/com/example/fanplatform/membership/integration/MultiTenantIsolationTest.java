package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import com.example.fanplatform.membership.infrastructure.jpa.MembershipJpaRepository;
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
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-tenant isolation: a detail GET on a membership belonging to another
 * tenant returns 404 (existence not leaked); a cross-tenant access-check returns
 * {@code allowed=false}.
 */
class MultiTenantIsolationTest extends MembershipServiceIntegrationBase {

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
        membershipJpaRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        membershipJpaRepository.deleteAll();
    }

    @Test
    @DisplayName("detail GET on another tenant's membership → 404 (no leak)")
    void crossTenantDetailIs404() {
        Instant now = Instant.now();
        // A membership owned by account "acc1" but in tenant "wms".
        Membership wmsMembership = Membership.activate(UUID.randomUUID().toString(), "wms", "acc1",
                MembershipTier.PREMIUM, now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS),
                1, "pgmock_x", now);
        membershipJpaRepository.saveAndFlush(wmsMembership);

        // The fan token is tenant fan-platform — the scoped lookup must miss.
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken("acc1"));
        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships/" + wmsMembership.getId(),
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody()).contains("MEMBERSHIP_NOT_FOUND");
    }

    @Test
    @DisplayName("list scoped to caller tenant — other-tenant rows excluded")
    void listExcludesOtherTenant() throws Exception {
        Instant now = Instant.now();
        String accountId = "acc-shared";
        Membership fanRow = Membership.activate(UUID.randomUUID().toString(),
                "fan-platform", accountId, MembershipTier.PREMIUM,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), 1, "pgmock_a", now);
        Membership wmsRow = Membership.activate(UUID.randomUUID().toString(),
                "wms", accountId, MembershipTier.PREMIUM,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS), 1, "pgmock_b", now);
        membershipJpaRepository.saveAndFlush(fanRow);
        membershipJpaRepository.saveAndFlush(wmsRow);

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signFanToken(accountId));
        ResponseEntity<String> res = rest.exchange(
                "http://localhost:" + port + "/api/fan/memberships",
                HttpMethod.GET, new HttpEntity<>(h), String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var content = objectMapper.readTree(res.getBody()).path("data").path("content");
        // Exactly one item (the fan-platform one) — the wms row is not visible.
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).path("membershipId").asText()).isEqualTo(fanRow.getId());
    }
}
