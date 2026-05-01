package com.example.security.infrastructure.persistence;

import com.example.security.domain.Tenants;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("LoginHistoryJpaRepository 쿼리 슬라이스 테스트")
class LoginHistoryJpaRepositoryTest {

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
    private LoginHistoryJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── existsByEventId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEventId — 존재하는 event_id → true")
    void existsByEventId_existing_returnsTrue() {
        String eventId = uuid();
        repo.saveAndFlush(entry(eventId, "acc-1", "SUCCESS"));

        assertThat(repo.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("existsByEventId — 없는 event_id → false")
    void existsByEventId_unknown_returnsFalse() {
        assertThat(repo.existsByEventId("ghost-" + uuid())).isFalse();
    }

    // ── findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc ──────────────────

    @Test
    @DisplayName("findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc — 가장 최신 SUCCESS 반환")
    void findFirstByAccountIdAndOutcomeOrderByOccurredAtDesc_returnsLatestEntry() {
        String accountId = "acc-" + uuid();
        Instant older = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant newer = Instant.now().minus(1, ChronoUnit.MINUTES);

        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", older));
        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", newer));
        repo.saveAndFlush(entryAt(uuid(), accountId, "FAILURE", newer.plusSeconds(30)));

        Optional<LoginHistoryJpaEntity> result =
                repo.findFirstByTenantIdAndAccountIdAndOutcomeOrderByOccurredAtDesc(
                        Tenants.DEFAULT_TENANT_ID, accountId, "SUCCESS");

        assertThat(result).isPresent();
        assertThat(result.get().getOccurredAt()).isEqualTo(newer.truncatedTo(ChronoUnit.MICROS));
        assertThat(result.get().getOutcome()).isEqualTo("SUCCESS");
    }

    // ── findByAccountIdAndFilters ─────────────────────────────────────────────

    @Test
    @DisplayName("findByAccountIdAndFilters — 모든 필터 null → 계정의 전체 행 반환")
    void findByAccountIdAndFilters_allNullFilters_returnsAllForAccount() {
        String accountId = "acc-" + uuid();
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);

        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", base));
        repo.saveAndFlush(entryAt(uuid(), accountId, "FAILURE", base.plusSeconds(30)));
        repo.saveAndFlush(entryAt(uuid(), "other-acc", "SUCCESS", base)); // 다른 계정

        Page<LoginHistoryJpaEntity> page = repo.findByTenantAndAccountAndFilters(
                Tenants.DEFAULT_TENANT_ID, accountId, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(LoginHistoryJpaEntity::getAccountId)
                .containsOnly(accountId);
    }

    @Test
    @DisplayName("findByAccountIdAndFilters — outcome 필터 적용")
    void findByAccountIdAndFilters_withOutcomeFilter_filtersCorrectly() {
        String accountId = "acc-" + uuid();
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);

        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", base));
        repo.saveAndFlush(entryAt(uuid(), accountId, "FAILURE", base.plusSeconds(10)));
        repo.saveAndFlush(entryAt(uuid(), accountId, "FAILURE", base.plusSeconds(20)));

        Page<LoginHistoryJpaEntity> page = repo.findByTenantAndAccountAndFilters(
                Tenants.DEFAULT_TENANT_ID, accountId, null, null, "FAILURE", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(LoginHistoryJpaEntity::getOutcome)
                .containsOnly("FAILURE");
    }

    @Test
    @DisplayName("findByAccountIdAndFilters — from/to 날짜 범위 필터 적용")
    void findByAccountIdAndFilters_withDateRange_filtersCorrectly() {
        String accountId = "acc-" + uuid();
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);

        Instant tOld = base;
        Instant tIn  = base.plus(30, ChronoUnit.MINUTES);
        Instant tNew = base.plus(90, ChronoUnit.MINUTES);

        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", tOld));
        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", tIn));
        repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS", tNew));

        Instant from = tOld.plusSeconds(1);
        Instant to   = tNew.minusSeconds(1);

        Page<LoginHistoryJpaEntity> page = repo.findByTenantAndAccountAndFilters(
                Tenants.DEFAULT_TENANT_ID, accountId, from, to, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getOccurredAt())
                .isEqualTo(tIn.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    @DisplayName("findByAccountIdAndFilters — 페이지네이션 동작")
    void findByAccountIdAndFilters_pagination_works() {
        String accountId = "acc-" + uuid();
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);

        for (int i = 0; i < 5; i++) {
            repo.saveAndFlush(entryAt(uuid(), accountId, "SUCCESS",
                    base.plusSeconds(i * 10L)));
        }

        Page<LoginHistoryJpaEntity> page0 = repo.findByTenantAndAccountAndFilters(
                Tenants.DEFAULT_TENANT_ID, accountId, null, null, null, PageRequest.of(0, 2));
        Page<LoginHistoryJpaEntity> page1 = repo.findByTenantAndAccountAndFilters(
                Tenants.DEFAULT_TENANT_ID, accountId, null, null, null, PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page0.getContent().get(0).getId())
                .isNotEqualTo(page1.getContent().get(0).getId());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private LoginHistoryJpaEntity entry(String eventId, String accountId, String outcome) {
        return entryAt(eventId, accountId, outcome, Instant.now());
    }

    private LoginHistoryJpaEntity entryAt(String eventId, String accountId,
                                           String outcome, Instant occurredAt) {
        return LoginHistoryJpaEntity.from(
                Tenants.DEFAULT_TENANT_ID,
                eventId, accountId, outcome,
                "1.2.3.x", "Chrome", "fp-" + eventId.substring(0, 8), "KR",
                occurredAt);
    }
}
