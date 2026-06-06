package com.example.admin.infrastructure.persistence;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
@DisplayName("AdminActionJpaRepository 쿼리 슬라이스 테스트")
class AdminActionJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("admin_db")
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
    private AdminActionJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    private Long operatorId;

    @BeforeEach
    void setup() {
        repo.deleteAll();
        operatorId = insertOperator();
    }

    // ── findByActorIdAndActionCodeAndIdempotencyKey ───────────────────────────

    @Test
    @DisplayName("findByActorIdAndActionCodeAndIdempotencyKey — 조합 키로 조회")
    void findByActorIdAndActionCodeAndIdempotencyKey_existing_returnsAction() {
        String actorId = "op-" + uuid();
        String idemp = "idemp-" + uuid();
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-1", idemp, Instant.now()));

        Optional<AdminActionJpaEntity> result =
                repo.findByActorIdAndActionCodeAndIdempotencyKey(actorId, "LOCK_ACCOUNT", idemp);

        assertThat(result).isPresent();
        assertThat(result.get().getActionCode()).isEqualTo("LOCK_ACCOUNT");
    }

    // ── findByLegacyAuditId ───────────────────────────────────────────────────

    @Test
    @DisplayName("findByLegacyAuditId — UUID 로 조회")
    void findByLegacyAuditId_existing_returnsAction() {
        String legacyId = uuid();
        repo.saveAndFlush(action(legacyId, "UNLOCK_ACCOUNT", "op-" + uuid(), "user-2",
                "idemp-" + uuid(), Instant.now()));

        Optional<AdminActionJpaEntity> result = repo.findByLegacyAuditId(legacyId);

        assertThat(result).isPresent();
        assertThat(result.get().getLegacyAuditId()).isEqualTo(legacyId);
    }

    // ── search — nullable 필터 ────────────────────────────────────────────────

    @Test
    @DisplayName("search — 모든 필터 null → 전체 행 반환")
    void search_allNullFilters_returnsAllRows() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        String actorId = "op-" + uuid();
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-A", "i1", base));
        repo.saveAndFlush(action(uuid(), "UNLOCK_ACCOUNT", actorId, "user-B", "i2", base.plusSeconds(30)));

        Page<AdminActionJpaEntity> page =
                repo.search(null, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search — targetId 필터 적용")
    void search_withTargetIdFilter_filtersCorrectly() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        String actorId = "op-" + uuid();
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-TARGET", "i1", base));
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-OTHER", "i2", base.plusSeconds(10)));

        Page<AdminActionJpaEntity> page =
                repo.search("user-TARGET", null, null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTargetId()).isEqualTo("user-TARGET");
    }

    @Test
    @DisplayName("search — actionCode 필터 적용")
    void search_withActionCodeFilter_filtersCorrectly() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        String actorId = "op-" + uuid();
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-1", "i1", base));
        repo.saveAndFlush(action(uuid(), "UNLOCK_ACCOUNT", actorId, "user-2", "i2", base.plusSeconds(10)));
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "user-3", "i3", base.plusSeconds(20)));

        Page<AdminActionJpaEntity> page =
                repo.search(null, "LOCK_ACCOUNT", null, null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(AdminActionJpaEntity::getActionCode)
                .containsOnly("LOCK_ACCOUNT");
    }

    @Test
    @DisplayName("search — from/to 날짜 범위 필터 적용")
    void search_withDateRange_filtersCorrectly() {
        Instant base = Instant.now().minus(2, ChronoUnit.HOURS);
        String actorId = "op-" + uuid();

        Instant tOld = base;
        Instant tIn  = base.plus(30, ChronoUnit.MINUTES);
        Instant tNew = base.plus(90, ChronoUnit.MINUTES);

        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "u1", "i1", tOld));
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "u2", "i2", tIn));
        repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId, "u3", "i3", tNew));

        Instant from = tOld.plusSeconds(1);
        Instant to   = tNew.minusSeconds(1);

        Page<AdminActionJpaEntity> page =
                repo.search(null, null, from, to, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getTargetId()).isEqualTo("u2");
    }

    @Test
    @DisplayName("search — 페이지네이션 동작")
    void search_pagination_works() {
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        String actorId = "op-" + uuid();

        for (int i = 0; i < 5; i++) {
            repo.saveAndFlush(action(uuid(), "LOCK_ACCOUNT", actorId,
                    "user-" + i, "idemp-" + i, base.plusSeconds(i * 10L)));
        }

        Page<AdminActionJpaEntity> page0 =
                repo.search(null, null, null, null, PageRequest.of(0, 2));
        Page<AdminActionJpaEntity> page1 =
                repo.search(null, null, null, null, PageRequest.of(1, 2));

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

    private AdminActionJpaEntity action(String legacyAuditId, String actionCode,
                                         String actorId, String targetId,
                                         String idempotencyKey, Instant startedAt) {
        return AdminActionJpaEntity.create(
                legacyAuditId, actionCode, actorId, "SUPER_ADMIN",
                operatorId, "account:lock",
                "USER_ACCOUNT", targetId,
                "test reason", null, idempotencyKey,
                "SUCCESS", null,
                startedAt, startedAt.plusSeconds(1));
    }

    private Long insertOperator() {
        String operatorUuid = AdminOperatorJpaEntity.newOperatorId();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO admin_operators " +
                        "(operator_id, tenant_id, email, password_hash, display_name, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                operatorUuid,
                "fan-platform",
                "op-" + operatorUuid.substring(0, 8) + "@example.com",
                "$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder.",
                "Test Op " + operatorUuid.substring(0, 8),
                "ACTIVE",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        return jdbc.queryForObject(
                "SELECT id FROM admin_operators WHERE operator_id = ?",
                Long.class, operatorUuid);
    }
}
