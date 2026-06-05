package com.example.membership.integration;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import com.example.membership.infrastructure.persistence.SubscriptionJpaEntity;
import com.example.membership.infrastructure.persistence.SubscriptionJpaRepository;
import com.example.membership.infrastructure.scheduler.SubscriptionExpiryScheduler;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test for {@link SubscriptionExpiryScheduler}. Seeds ACTIVE rows
 * with expires_at in the past and verifies the scheduler transitions them to
 * EXPIRED and emits an outbox event. FREE rows (expires_at IS NULL) must be
 * excluded.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(MembershipJwtTestSupport.JwtDecoderConfig.class)
@DisplayName("SubscriptionExpiryScheduler — end-to-end")
class SubscriptionExpirySchedulerIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("membership.account-service.base-url", () -> "http://localhost:0");
    }

    @Autowired
    SubscriptionExpiryScheduler scheduler;

    @Autowired
    SubscriptionRepository subscriptionRepository;

    @Autowired
    SubscriptionJpaRepository subscriptionJpaRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final SubscriptionStatusMachine machine = new SubscriptionStatusMachine();

    @BeforeEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM subscription_status_history");
        jdbcTemplate.update("DELETE FROM subscriptions");
        jdbcTemplate.update("DELETE FROM outbox");
    }

    @Test
    @DisplayName("past-due ACTIVE subscription → scheduler transitions it to EXPIRED + emits outbox event")
    void expiresPastDueSubscription() {
        LocalDateTime now = LocalDateTime.now();
        String accountId = UUID.randomUUID().toString();

        Subscription sub = Subscription.activate(
                accountId, PlanLevel.FAN_CLUB, 30, now.minusDays(31), machine);
        setField(sub, "expiresAt", now.minusHours(1));
        subscriptionJpaRepository.save(SubscriptionJpaEntity.fromDomain(sub));

        scheduler.expireSubscriptions();

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            Subscription reloaded = subscriptionRepository.findById(sub.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);

            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                    "SELECT event_type FROM outbox WHERE event_type = 'membership.subscription.expired'");
            assertThat(events).hasSize(1);

            List<Map<String, Object>> history = jdbcTemplate.queryForList(
                    "SELECT reason, to_status FROM subscription_status_history WHERE subscription_id = ?",
                    sub.getId());
            assertThat(history).hasSize(1);
            assertThat(history.get(0).get("reason")).isEqualTo("SCHEDULED_EXPIRE");
            assertThat(history.get(0).get("to_status")).isEqualTo("EXPIRED");
        });
    }

    @Test
    @DisplayName("FREE subscription (expires_at IS NULL) is excluded by the scheduler")
    void freeSubscriptionIsExcluded() {
        LocalDateTime now = LocalDateTime.now();
        String accountId = UUID.randomUUID().toString();

        Subscription free = Subscription.activate(
                accountId, PlanLevel.FREE, 0, now.minusDays(10), machine);
        subscriptionJpaRepository.save(SubscriptionJpaEntity.fromDomain(free));

        scheduler.expireSubscriptions();

        Subscription reloaded = subscriptionRepository.findById(free.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(reloaded.getExpiresAt()).isNull();

        Integer expiredEvents = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = 'membership.subscription.expired'",
                Integer.class);
        assertThat(expiredEvents).isEqualTo(0);
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
