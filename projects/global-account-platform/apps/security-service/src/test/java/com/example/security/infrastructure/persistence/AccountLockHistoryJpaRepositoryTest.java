package com.example.security.infrastructure.persistence;

import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slice test for {@link AccountLockHistoryJpaRepository} against real MySQL via
 * Testcontainers (TASK-BE-041b-fix Warning 1). H2 is forbidden per
 * platform/testing-strategy.md — event_id unique constraint and
 * index-backed DESC ordering need to run on the production engine.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code findByAccountIdOrderByOccurredAtDesc} returns rows in strict
 *       occurred_at DESC order and excludes rows for other accounts.</li>
 *   <li>Inserting two rows with the same {@code event_id} raises
 *       {@link DataIntegrityViolationException} (idempotency foundation).</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("AccountLockHistoryJpaRepository")
@ExtendWith(DockerAvailableCondition.class)
class AccountLockHistoryJpaRepositoryTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("security_db")
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
    private AccountLockHistoryJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    @Test
    @DisplayName("findByAccountIdOrderByOccurredAtDesc returns rows in occurred_at DESC order for the account only")
    void findByAccountIdOrdersDesc() {
        Instant t0 = Instant.parse("2026-04-10T00:00:00Z");
        save(entity("11111111-1111-1111-1111-111111111111", "acc-1", "ADMIN_LOCK",
                "op-1", "admin", t0));
        save(entity("22222222-2222-2222-2222-222222222222", "acc-1", "AUTO_DETECT",
                "00000000-0000-0000-0000-000000000000", "system", t0.plusSeconds(600)));
        save(entity("33333333-3333-3333-3333-333333333333", "acc-1", "ADMIN_LOCK",
                "op-2", "admin", t0.plusSeconds(60)));
        // Other account — must not appear in the result
        save(entity("44444444-4444-4444-4444-444444444444", "acc-other", "ADMIN_LOCK",
                "op-9", "admin", t0.plusSeconds(1000)));

        List<AccountLockHistoryJpaEntity> rows = repo.findByAccountIdOrderByOccurredAtDesc("acc-1");

        assertThat(rows).extracting(AccountLockHistoryJpaEntity::getEventId)
                .containsExactly(
                        "22222222-2222-2222-2222-222222222222",
                        "33333333-3333-3333-3333-333333333333",
                        "11111111-1111-1111-1111-111111111111");
    }

    @Test
    @DisplayName("Duplicate event_id insert raises DataIntegrityViolationException")
    void duplicateEventIdRejected() {
        Instant t = Instant.parse("2026-04-10T12:00:00Z");
        save(entity("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "acc-dup", "ADMIN_LOCK",
                "op-1", "admin", t));

        AccountLockHistoryJpaEntity duplicate = entity(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "acc-dup", "ADMIN_LOCK",
                "op-2", "admin", t.plusSeconds(60));

        assertThatThrownBy(() -> repo.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AccountLockHistoryJpaEntity save(AccountLockHistoryJpaEntity e) {
        return repo.saveAndFlush(e);
    }

    private static AccountLockHistoryJpaEntity entity(String eventId, String accountId,
                                                       String reason, String lockedBy,
                                                       String source, Instant occurredAt) {
        return AccountLockHistoryJpaEntity.create(eventId, accountId, reason, lockedBy, source, occurredAt);
    }
}
