package com.example.membership.integration;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import com.example.membership.infrastructure.persistence.SubscriptionJpaEntity;
import com.example.membership.infrastructure.persistence.SubscriptionJpaRepository;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(MembershipJwtTestSupport.JwtDecoderConfig.class)
@DisplayName("구독 재활성화 통합 테스트 — EXPIRED 구독 보유 계정의 신규 구독 생성")
class SubscriptionReactivationIntegrationTest extends AbstractIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("membership.account-service.base-url", wireMock::baseUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SubscriptionJpaRepository subscriptionJpaRepository;

    private final SubscriptionStatusMachine machine = new SubscriptionStatusMachine();

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        jdbcTemplate.execute("TRUNCATE TABLE subscription_status_history");
        jdbcTemplate.update("DELETE FROM subscriptions");
        jdbcTemplate.update("DELETE FROM outbox");
    }

    @Test
    @DisplayName("EXPIRED 구독 보유 계정 재활성화 요청 → 201 신규 ACTIVE 구독 생성 (EXPIRED 행 유지)")
    void expiredSubscription_reactivation_createsNewActiveSubscription() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String idem = "idem-reactivate-" + UUID.randomUUID();

        // EXPIRED 구독 미리 생성
        LocalDateTime now = LocalDateTime.now();
        Subscription expired = Subscription.activate(accountId, PlanLevel.FAN_CLUB, 30, now.minusDays(31), machine);
        setField(expired, "expiresAt", now.minusHours(1));
        expired.expire(now.minusHours(1), machine);
        subscriptionJpaRepository.save(SubscriptionJpaEntity.fromDomain(expired));

        // account-service ACTIVE 스텁
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/accounts/" + accountId + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + accountId + "\",\"status\":\"ACTIVE\"}")));

        // 재활성화 요청
        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", MembershipJwtTestSupport.bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"" + idem + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planLevel").value("FAN_CLUB"));

        // EXPIRED 행 유지 + 신규 ACTIVE 행 생성 — 총 2행
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ?",
                Integer.class, accountId);
        assertThat(total).isEqualTo(2);

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ? AND status = 'ACTIVE'",
                Integer.class, accountId);
        assertThat(activeCount).isEqualTo(1);

        Integer expiredCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ? AND status = 'EXPIRED'",
                Integer.class, accountId);
        assertThat(expiredCount).isEqualTo(1);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
