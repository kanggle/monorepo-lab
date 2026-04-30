package com.example.security.infrastructure.persistence;

import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("SuspiciousEventJpaRepository 쿼리 슬라이스 테스트")
class SuspiciousEventJpaRepositoryTest {

    @SuppressWarnings("resource")
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
    private SuspiciousEventJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc ──────────────

    @Test
    @DisplayName("findByAccountIdAndDetectedAtBetween — 범위 내 이벤트 내림차순 반환")
    void findByAccountIdAndDetectedAtBetween_inRange_returnsSortedDesc() {
        String accountId = uuid();
        Instant base = Instant.now().minusSeconds(100);
        Instant t1 = base;
        Instant t2 = base.plusSeconds(30);
        Instant t3 = base.plusSeconds(60);
        // Insert out-of-order to verify ORDER BY
        repo.saveAndFlush(event(accountId, t3));
        repo.saveAndFlush(event(accountId, t1));
        repo.saveAndFlush(event(accountId, t2));

        List<SuspiciousEventJpaEntity> result = repo
                .findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                        accountId, t1.minusSeconds(1), t3.plusSeconds(1));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getDetectedAt()).isEqualTo(t3);
        assertThat(result.get(1).getDetectedAt()).isEqualTo(t2);
        assertThat(result.get(2).getDetectedAt()).isEqualTo(t1);
    }

    @Test
    @DisplayName("findByAccountIdAndDetectedAtBetween — 범위 밖 이벤트 제외")
    void findByAccountIdAndDetectedAtBetween_outsideRange_excluded() {
        String accountId = uuid();
        Instant now = Instant.now();
        repo.saveAndFlush(event(accountId, now.minusSeconds(200)));
        repo.saveAndFlush(event(accountId, now.plusSeconds(200)));

        List<SuspiciousEventJpaEntity> result = repo
                .findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                        accountId, now.minusSeconds(100), now.plusSeconds(100));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByAccountIdAndDetectedAtBetween — 다른 계정의 이벤트 제외")
    void findByAccountIdAndDetectedAtBetween_differentAccount_excluded() {
        String accountA = uuid();
        String accountB = uuid();
        Instant now = Instant.now();
        repo.saveAndFlush(event(accountA, now));

        List<SuspiciousEventJpaEntity> result = repo
                .findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                        accountB, now.minusSeconds(60), now.plusSeconds(60));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByAccountIdAndDetectedAtBetween — 이벤트 없음 → 빈 목록")
    void findByAccountIdAndDetectedAtBetween_noEvents_returnsEmpty() {
        Instant now = Instant.now();
        assertThat(repo.findByAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                uuid(), now.minusSeconds(60), now)).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SuspiciousEventJpaEntity event(String accountId, Instant detectedAt) {
        return SuspiciousEventJpaEntity.create(
                UUID.randomUUID().toString(),
                accountId,
                "BRUTE_FORCE",
                70,
                "SOFT_LOCK",
                null,
                null,
                detectedAt,
                null);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
