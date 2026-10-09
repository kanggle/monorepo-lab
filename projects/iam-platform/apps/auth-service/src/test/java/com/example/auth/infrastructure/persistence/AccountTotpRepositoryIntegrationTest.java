package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.mfa.AccountTotp;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-MONO-771 S2b — {@code account_totp} (V0043) round trip on real MySQL 8 with Flyway and
 * {@code ddl-auto=validate}: the migration and the entity agree (VARBINARY secret, JSON recovery-code array,
 * nullable pending columns, {@code @Version}), and the optimistic lock refuses a stale writer.
 *
 * <p>⚪ Not run on the authoring host (no Docker daemon) — its first execution is CI's {@code integrationTest}.
 */
@Tag("integration")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@Import(AccountTotpRepositoryImpl.class)
@DisplayName("account_totp 영속 왕복 (TASK-MONO-771 S2b, MySQL)")
class AccountTotpRepositoryIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("auth_db")
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

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000771";
    private static final Instant T0 = Instant.parse("2026-10-08T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private AccountTotpRepositoryImpl repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("대기 행 저장 → 읽기(confirmed_at·복구 코드 NULL) → 확정 → 다시 읽기 · JSON 배열 · last_used_step")
    void pendingThenConfirmed_roundTrip() {
        byte[] sealed = new byte[]{9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        repository.save(AccountTotp.pending(ACCOUNT, "fan-platform", sealed, "v1", T0));

        AccountTotp pending = repository.findByAccountId(ACCOUNT).orElseThrow();
        assertThat(pending.isConfirmed()).isFalse();
        assertThat(pending.hasNoRecoveryCodeColumn()).isTrue();
        assertThat(pending.getSecretCiphertext()).isEqualTo(sealed);
        assertThat(jdbc.queryForObject("SELECT recovery_codes_hashed IS NULL FROM account_totp WHERE account_id = ?",
                Boolean.class, ACCOUNT)).isTrue();

        pending.confirm(57_600_000L, List.of("$argon2id$a", "$argon2id$b"), T0.plusSeconds(5));
        repository.save(pending);

        AccountTotp confirmed = repository.findByAccountId(ACCOUNT).orElseThrow();
        assertThat(confirmed.isConfirmed()).isTrue();
        assertThat(confirmed.getLastUsedStep()).isEqualTo(57_600_000L);
        assertThat(confirmed.getRecoveryCodeHashes()).containsExactly("$argon2id$a", "$argon2id$b");
        assertThat(confirmed.getTenantId()).isEqualTo("fan-platform");
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(recovery_codes_hashed) FROM account_totp "
                + "WHERE account_id = ?", Integer.class, ACCOUNT)).isEqualTo(2);
    }

    @Test
    @DisplayName("낙관적 락: 같은 버전을 읽은 두 쓰기 중 두 번째는 거절 (같은 코드 동시 제출에서 한 쪽만 이긴다)")
    void staleWriter_refused() {
        String account = "0199de70-0000-7000-8000-000000000772";
        AccountTotp created = AccountTotp.pending(account, "fan-platform", new byte[]{1, 2, 3}, "v1", T0);
        repository.save(created);
        AccountTotp first = repository.findByAccountId(account).orElseThrow();
        AccountTotp second = repository.findByAccountId(account).orElseThrow();
        first.confirm(1L, List.of("h"), T0);
        second.confirm(1L, List.of("h"), T0);

        repository.save(first);

        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("대기 행 교체 = 같은 트랜잭션의 delete → insert (중복 키 없음)")
    void replacePending() {
        String account = "0199de70-0000-7000-8000-000000000773";
        repository.save(AccountTotp.pending(account, "fan-platform", new byte[]{1}, "v1", T0));

        repository.deleteByAccountId(account);
        repository.save(AccountTotp.pending(account, "fan-platform", new byte[]{2}, "v1", T0.plusSeconds(60)));

        assertThat(repository.findByAccountId(account).orElseThrow().getSecretCiphertext()).containsExactly(2);
    }
}
