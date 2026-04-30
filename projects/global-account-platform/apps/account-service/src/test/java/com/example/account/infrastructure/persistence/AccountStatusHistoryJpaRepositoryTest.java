package com.example.account.infrastructure.persistence;

import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AccountStatusHistoryJpaRepository 쿼리 슬라이스 테스트")
class AccountStatusHistoryJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("account_db")
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
    private AccountStatusHistoryJpaRepository historyRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        // append-only 트리거 우회 — TRUNCATE 는 row-level 트리거 발생 안 함
        jdbc.execute("TRUNCATE TABLE account_status_history");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.update("DELETE FROM accounts");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    // ── findByAccountIdOrderByOccurredAtDesc ────────────────────────────────

    @Test
    @DisplayName("findByAccountIdOrderByOccurredAtDesc — 여러 항목 → occurred_at 내림차순 반환")
    void findByAccountIdOrderByOccurredAtDesc_multipleEntries_returnsDescOrder() {
        String accountId = UUID.randomUUID().toString();
        seedAccount(accountId);

        Instant older = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant newer = Instant.now().minus(1, ChronoUnit.MINUTES);

        historyRepo.save(historyEntity(accountId, AccountStatus.ACTIVE, AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, older));
        historyRepo.save(historyEntity(accountId, AccountStatus.LOCKED, AccountStatus.ACTIVE,
                StatusChangeReason.ADMIN_UNLOCK, newer));

        List<AccountStatusHistoryJpaEntity> result =
                historyRepo.findByAccountIdOrderByOccurredAtDesc(accountId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getOccurredAt()).isAfterOrEqualTo(result.get(1).getOccurredAt());
        assertThat(result.get(0).getToStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.get(1).getToStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    @Test
    @DisplayName("findTopByAccountIdOrderByOccurredAtDesc — 가장 최신 단일 항목 반환")
    void findTopByAccountIdOrderByOccurredAtDesc_returnsLatestEntry() {
        String accountId = UUID.randomUUID().toString();
        seedAccount(accountId);

        historyRepo.save(historyEntity(accountId, AccountStatus.ACTIVE, AccountStatus.LOCKED,
                StatusChangeReason.ADMIN_LOCK, Instant.now().minus(10, ChronoUnit.MINUTES)));
        historyRepo.save(historyEntity(accountId, AccountStatus.LOCKED, AccountStatus.ACTIVE,
                StatusChangeReason.ADMIN_UNLOCK, Instant.now().minus(1, ChronoUnit.MINUTES)));

        Optional<AccountStatusHistoryJpaEntity> result =
                historyRepo.findTopByAccountIdOrderByOccurredAtDesc(accountId);

        assertThat(result).isPresent();
        assertThat(result.get().getToStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.get().getReasonCode()).isEqualTo(StatusChangeReason.ADMIN_UNLOCK);
    }

    // ── append-only 트리거 (A3) ─────────────────────────────────────────────

    @Test
    @DisplayName("트리거 — account_status_history UPDATE 시도 → 예외 발생 (append-only A3)")
    void appendOnlyTrigger_update_throwsException() {
        String accountId = UUID.randomUUID().toString();
        seedAccount(accountId);

        AccountStatusHistoryJpaEntity saved = historyRepo.save(
                historyEntity(accountId, AccountStatus.ACTIVE, AccountStatus.LOCKED,
                        StatusChangeReason.ADMIN_LOCK, Instant.now()));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE account_status_history SET actor_type = 'TAMPERED' WHERE id = ?",
                        saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("트리거 — account_status_history DELETE 시도 → 예외 발생 (append-only A3)")
    void appendOnlyTrigger_delete_throwsException() {
        String accountId = UUID.randomUUID().toString();
        seedAccount(accountId);

        AccountStatusHistoryJpaEntity saved = historyRepo.save(
                historyEntity(accountId, AccountStatus.ACTIVE, AccountStatus.LOCKED,
                        StatusChangeReason.ADMIN_LOCK, Instant.now()));

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM account_status_history WHERE id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void seedAccount(String accountId) {
        jdbc.update(
                "INSERT INTO accounts (id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, 'ACTIVE', NOW(), NOW(), 0)",
                accountId, accountId + "@ex.com");
    }

    private AccountStatusHistoryJpaEntity historyEntity(String accountId,
                                                         AccountStatus from, AccountStatus to,
                                                         StatusChangeReason reason, Instant occurredAt) {
        AccountStatusHistoryEntry entry = AccountStatusHistoryEntry.reconstitute(
                null, accountId, from, to, reason, "SYSTEM", null, null, occurredAt);
        return AccountStatusHistoryJpaEntity.fromDomain(entry);
    }
}
