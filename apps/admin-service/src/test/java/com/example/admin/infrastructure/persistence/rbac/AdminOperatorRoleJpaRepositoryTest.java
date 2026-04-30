package com.example.admin.infrastructure.persistence.rbac;

import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminOperatorRoleJpaRepository 쿼리 슬라이스 테스트")
class AdminOperatorRoleJpaRepositoryTest {

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
    private AdminOperatorRoleJpaRepository repo;

    @Autowired
    private AdminOperatorJpaRepository operatorRepo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
        operatorRepo.deleteAll();
        jdbc.update("DELETE FROM admin_roles WHERE name LIKE 'TEST_%'");
    }

    // ── findByOperatorId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOperatorId — 오퍼레이터의 역할 바인딩 반환")
    void findByOperatorId_existing_returnsBindings() {
        Long opId = saveOperator("op1@example.com");
        Long roleA = insertRole("TEST_ROLE_A");
        Long roleB = insertRole("TEST_ROLE_B");

        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opId, roleA, Instant.now(), null));
        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opId, roleB, Instant.now(), null));

        List<AdminOperatorRoleJpaEntity> result = repo.findByOperatorId(opId);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AdminOperatorRoleJpaEntity::getOperatorId)
                .containsOnly(opId);
    }

    @Test
    @DisplayName("findByOperatorId — 역할 없는 오퍼레이터 → 빈 목록")
    void findByOperatorId_noBindings_returnsEmpty() {
        Long opId = saveOperator("op2@example.com");

        List<AdminOperatorRoleJpaEntity> result = repo.findByOperatorId(opId);

        assertThat(result).isEmpty();
    }

    // ── findByOperatorIdIn ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOperatorIdIn — 복수 오퍼레이터 역할 일괄 조회")
    void findByOperatorIdIn_multipleOperators_returnsAllBindings() {
        Long opA = saveOperator("opA@example.com");
        Long opB = saveOperator("opB@example.com");
        Long role = insertRole("TEST_ROLE_C");

        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opA, role, Instant.now(), null));
        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opB, role, Instant.now(), null));

        List<AdminOperatorRoleJpaEntity> result = repo.findByOperatorIdIn(List.of(opA, opB));

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AdminOperatorRoleJpaEntity::getOperatorId)
                .containsExactlyInAnyOrder(opA, opB);
    }

    @Test
    @DisplayName("findByOperatorIdIn — 요청 목록에 없는 오퍼레이터는 제외")
    void findByOperatorIdIn_excludesUnrequestedOperators() {
        Long opA = saveOperator("opX@example.com");
        Long opB = saveOperator("opY@example.com");
        Long role = insertRole("TEST_ROLE_D");

        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opA, role, Instant.now(), null));
        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opB, role, Instant.now(), null));

        List<AdminOperatorRoleJpaEntity> result = repo.findByOperatorIdIn(List.of(opA));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperatorId()).isEqualTo(opA);
    }

    // ── deleteByOperatorId ────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteByOperatorId — 오퍼레이터 역할 전체 삭제, 삭제 수 반환")
    void deleteByOperatorId_deletesAllBindings_returnsCount() {
        Long opId = saveOperator("opDel@example.com");
        Long roleE = insertRole("TEST_ROLE_E");
        Long roleF = insertRole("TEST_ROLE_F");

        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opId, roleE, Instant.now(), null));
        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opId, roleF, Instant.now(), null));

        int deleted = repo.deleteByOperatorId(opId);

        assertThat(deleted).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteByOperatorId — 다른 오퍼레이터 역할은 삭제하지 않음")
    void deleteByOperatorId_doesNotAffectOtherOperators() {
        Long opA = saveOperator("opKeep@example.com");
        Long opB = saveOperator("opRemove@example.com");
        Long role = insertRole("TEST_ROLE_G");

        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opA, role, Instant.now(), null));
        repo.saveAndFlush(AdminOperatorRoleJpaEntity.create(opB, role, Instant.now(), null));

        int deleted = repo.deleteByOperatorId(opB);

        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findByOperatorId(opA)).hasSize(1);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long saveOperator(String email) {
        AdminOperatorJpaEntity op = AdminOperatorJpaEntity.create(
                AdminOperatorJpaEntity.newOperatorId(),
                email,
                "$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder.",
                "Test Op",
                "ACTIVE",
                Instant.now());
        return operatorRepo.saveAndFlush(op).getId();
    }

    private Long insertRole(String name) {
        jdbc.update(
                "INSERT INTO admin_roles (name, description, require_2fa, created_at) VALUES (?, ?, false, ?)",
                name, "Test role", java.sql.Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT id FROM admin_roles WHERE name = ?", Long.class, name);
    }
}
