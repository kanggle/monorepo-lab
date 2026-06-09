package com.example.fanplatform.membership.integration;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
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
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Internal access-check (tier hierarchy / expired / canceled / cross-account /
 * cross-tenant) via the workload-identity {@code /internal/membership/access}
 * endpoint. The DB-down fail-closed path is unit-covered
 * ({@code CheckAccessUseCaseTest}); this IT covers the live domain-deny cases.
 */
class AccessCheckIntegrationTest extends MembershipServiceIntegrationBase {

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

    private void seed(String accountId, String tenantId, MembershipTier tier,
                      com.example.fanplatform.membership.domain.membership.status.MembershipStatus status,
                      Instant from, Instant to) {
        Membership m = Membership.activate(UUID.randomUUID().toString(), tenantId, accountId,
                tier, from, to, 1, "pgmock_x", from);
        if (status == com.example.fanplatform.membership.domain.membership.status.MembershipStatus.CANCELED) {
            m.cancel(Instant.now());
        }
        membershipJpaRepository.saveAndFlush(m);
    }

    private boolean checkAccess(String accountId, String tier, String tenantId) throws Exception {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(jwt.signWorkloadToken("svc-community"));
        String url = "http://localhost:" + port + "/internal/membership/access"
                + "?accountId=" + accountId + "&tier=" + tier + "&tenantId=" + tenantId;
        ResponseEntity<String> res = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(res.getBody());
        return body.path("allowed").asBoolean();
    }

    @Test
    @DisplayName("PREMIUM ACTIVE in-window grants MEMBERS_ONLY (tier hierarchy)")
    void premiumGrantsMembers() throws Exception {
        Instant now = Instant.now();
        seed("acc-p", "fan-platform", MembershipTier.PREMIUM,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));

        assertThat(checkAccess("acc-p", "MEMBERS_ONLY", "fan-platform")).isTrue();
        assertThat(checkAccess("acc-p", "PREMIUM", "fan-platform")).isTrue();
    }

    @Test
    @DisplayName("MEMBERS_ONLY ACTIVE denies PREMIUM")
    void membersDeniesPremium() throws Exception {
        Instant now = Instant.now();
        seed("acc-m", "fan-platform", MembershipTier.MEMBERS_ONLY,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));

        assertThat(checkAccess("acc-m", "PREMIUM", "fan-platform")).isFalse();
        assertThat(checkAccess("acc-m", "MEMBERS_ONLY", "fan-platform")).isTrue();
    }

    @Test
    @DisplayName("expired window denies (status stays ACTIVE in storage)")
    void expiredDenies() throws Exception {
        Instant now = Instant.now();
        seed("acc-e", "fan-platform", MembershipTier.PREMIUM,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.ACTIVE,
                now.minus(40, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS));

        assertThat(checkAccess("acc-e", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("CANCELED denies")
    void canceledDenies() throws Exception {
        Instant now = Instant.now();
        seed("acc-c", "fan-platform", MembershipTier.PREMIUM,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.CANCELED,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));

        assertThat(checkAccess("acc-c", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("no membership row denies; cross-account denies")
    void noRowAndCrossAccountDeny() throws Exception {
        assertThat(checkAccess("ghost", "MEMBERS_ONLY", "fan-platform")).isFalse();

        Instant now = Instant.now();
        seed("owner", "fan-platform", MembershipTier.PREMIUM,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));
        assertThat(checkAccess("other", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("cross-tenant lookup denies (no leak)")
    void crossTenantDenies() throws Exception {
        Instant now = Instant.now();
        seed("acc-x", "fan-platform", MembershipTier.PREMIUM,
                com.example.fanplatform.membership.domain.membership.status.MembershipStatus.ACTIVE,
                now.minus(1, ChronoUnit.DAYS), now.plus(10, ChronoUnit.DAYS));

        assertThat(checkAccess("acc-x", "PREMIUM", "wms")).isFalse();
    }
}
