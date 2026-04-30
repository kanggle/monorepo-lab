package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.plan.PlanLevel;
import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.membership.domain.subscription.status.SubscriptionStatusMachine;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for {@link SubscriptionJpaRepository#findExpirable} against a real
 * MySQL (Testcontainers). Verifies the expiry batch query excludes FREE
 * subscriptions (expires_at IS NULL), already-EXPIRED rows, and rows with
 * expires_at still in the future.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("SubscriptionJpaRepository#findExpirable")
@ExtendWith(DockerAvailableCondition.class)
class SubscriptionJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("membership_db")
            .withUsername("test")
            .withPassword("test")
            .withCommand("mysqld", "--log-bin-trust-function-creators=1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private SubscriptionJpaRepository repo;

    private final SubscriptionStatusMachine machine = new SubscriptionStatusMachine();

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("returns ACTIVE FAN_CLUB rows whose expires_at <= cutoff")
    void returnsExpirableActiveRows() {
        LocalDateTime now = LocalDateTime.now();

        Subscription expired1 = Subscription.activate(
                "acc-1", PlanLevel.FAN_CLUB, 30, now.minusDays(31), machine);
        // force expires_at into the past
        setField(expired1, "expiresAt", now.minusMinutes(5));

        Subscription expired2 = Subscription.activate(
                "acc-2", PlanLevel.FAN_CLUB, 30, now.minusDays(60), machine);
        setField(expired2, "expiresAt", now.minusHours(1));

        Subscription future = Subscription.activate(
                "acc-3", PlanLevel.FAN_CLUB, 30, now, machine);
        // expires_at = now + 30d

        repo.save(SubscriptionJpaEntity.fromDomain(expired1));
        repo.save(SubscriptionJpaEntity.fromDomain(expired2));
        repo.save(SubscriptionJpaEntity.fromDomain(future));

        List<SubscriptionJpaEntity> rows = repo.findExpirable(
                SubscriptionStatus.ACTIVE, now, PageRequest.of(0, 100));

        assertThat(rows).extracting(SubscriptionJpaEntity::getAccountId)
                .containsExactly("acc-2", "acc-1"); // ordered by expires_at ASC
    }

    @Test
    @DisplayName("excludes FREE rows (expires_at IS NULL)")
    void excludesFreeRows() {
        LocalDateTime now = LocalDateTime.now();

        Subscription free = Subscription.activate(
                "acc-free", PlanLevel.FREE, 0, now, machine);
        Subscription paidExpired = Subscription.activate(
                "acc-paid", PlanLevel.FAN_CLUB, 30, now.minusDays(31), machine);
        setField(paidExpired, "expiresAt", now.minusMinutes(5));

        repo.save(SubscriptionJpaEntity.fromDomain(free));
        repo.save(SubscriptionJpaEntity.fromDomain(paidExpired));

        List<SubscriptionJpaEntity> rows = repo.findExpirable(
                SubscriptionStatus.ACTIVE, now, PageRequest.of(0, 100));

        assertThat(rows).extracting(SubscriptionJpaEntity::getAccountId)
                .containsExactly("acc-paid");
    }

    @Test
    @DisplayName("excludes rows already in EXPIRED status")
    void excludesAlreadyExpired() {
        LocalDateTime now = LocalDateTime.now();

        Subscription stillActive = Subscription.activate(
                "acc-active", PlanLevel.FAN_CLUB, 30, now.minusDays(31), machine);
        setField(stillActive, "expiresAt", now.minusMinutes(5));

        Subscription alreadyExpired = Subscription.activate(
                "acc-done", PlanLevel.FAN_CLUB, 30, now.minusDays(60), machine);
        setField(alreadyExpired, "expiresAt", now.minusHours(1));
        alreadyExpired.expire(now.minusMinutes(30), machine);

        repo.save(SubscriptionJpaEntity.fromDomain(stillActive));
        repo.save(SubscriptionJpaEntity.fromDomain(alreadyExpired));

        List<SubscriptionJpaEntity> rows = repo.findExpirable(
                SubscriptionStatus.ACTIVE, now, PageRequest.of(0, 100));

        assertThat(rows).extracting(SubscriptionJpaEntity::getAccountId)
                .containsExactly("acc-active");
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
