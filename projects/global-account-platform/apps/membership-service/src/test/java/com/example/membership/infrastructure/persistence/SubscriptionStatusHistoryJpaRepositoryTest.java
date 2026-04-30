package com.example.membership.infrastructure.persistence;

import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("SubscriptionStatusHistoryJpaRepository 슬라이스 테스트")
class SubscriptionStatusHistoryJpaRepositoryTest {

    @SuppressWarnings("resource")
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
    private SubscriptionStatusHistoryJpaRepository repo;

    // No @BeforeEach cleanup — BEFORE DELETE trigger forbids DELETE
    // Test isolation via unique subscriptionId per test

    // ── save (append-only) ────────────────────────────────────────────────────

    @Test
    @DisplayName("saveAndFlush — 이력 저장 및 ID 발급")
    void saveAndFlush_entry_persistedWithGeneratedId() {
        String subscriptionId = uuid();
        SubscriptionStatusHistoryJpaEntity entity = entry(subscriptionId,
                SubscriptionStatus.ACTIVE, SubscriptionStatus.CANCELLED, "USER_REQUEST");

        SubscriptionStatusHistoryJpaEntity saved = repo.saveAndFlush(entity);

        assertThat(saved.getId()).isNotNull();
        Optional<SubscriptionStatusHistoryJpaEntity> found = repo.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(found.get().getFromStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(found.get().getToStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("saveAndFlush — 동일 구독의 여러 이력 모두 저장")
    void saveAndFlush_multipleEntries_allPersisted() {
        String subscriptionId = uuid();
        repo.saveAndFlush(entry(subscriptionId, SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED, "RENEWAL_FAILED"));
        repo.saveAndFlush(entry(subscriptionId, SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED, "USER_REQUEST"));

        List<SubscriptionStatusHistoryJpaEntity> all = repo.findAll();
        long count = all.stream()
                .filter(e -> e.getSubscriptionId().equals(subscriptionId))
                .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("saveAndFlush — 모든 필드 정확히 저장")
    void saveAndFlush_allFieldsMappedCorrectly() {
        String subscriptionId = uuid();
        String accountId = uuid();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SubscriptionStatusHistoryEntry historyEntry = new SubscriptionStatusHistoryEntry(
                subscriptionId, accountId,
                SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED,
                "PAYMENT_FAILED", "SYSTEM", now);

        SubscriptionStatusHistoryJpaEntity saved =
                repo.saveAndFlush(SubscriptionStatusHistoryJpaEntity.from(historyEntry));

        SubscriptionStatusHistoryJpaEntity found = repo.findById(saved.getId()).orElseThrow();
        assertThat(found.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(found.getAccountId()).isEqualTo(accountId);
        assertThat(found.getFromStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(found.getToStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(found.getReason()).isEqualTo("PAYMENT_FAILED");
        assertThat(found.getActorType()).isEqualTo("SYSTEM");
        assertThat(found.getOccurredAt()).isEqualTo(now);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SubscriptionStatusHistoryJpaEntity entry(String subscriptionId,
                                                             SubscriptionStatus from,
                                                             SubscriptionStatus to,
                                                             String reason) {
        return SubscriptionStatusHistoryJpaEntity.from(new SubscriptionStatusHistoryEntry(
                subscriptionId, UUID.randomUUID().toString(),
                from, to, reason, "SYSTEM", LocalDateTime.now()));
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
