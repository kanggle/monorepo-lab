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

/**
 * TASK-BE-326 / ADR-MONO-020 D1 — repository slice IT for
 * {@code operator_tenant_assignment}. Mirrors {@link AdminOperatorRoleJpaRepositoryTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("OperatorTenantAssignmentJpaRepository 쿼리 슬라이스 테스트")
class OperatorTenantAssignmentJpaRepositoryTest {

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
    private OperatorTenantAssignmentJpaRepository repo;

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

    @Test
    @DisplayName("findByOperatorId — 오퍼레이터의 테넌트 배정 tenantId 반환 (permission_set_id NULL)")
    void findByOperatorId_existing_returnsAssignments() {
        Long opId = saveOperator("op1@example.com");

        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opId, "wms", Instant.now(), null, null));
        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opId, "scm", Instant.now(), null, null));

        List<OperatorTenantAssignmentJpaEntity> result = repo.findByOperatorId(opId);

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(OperatorTenantAssignmentJpaEntity::getTenantId)
                .containsExactlyInAnyOrder("wms", "scm");
        assertThat(result)
                .extracting(OperatorTenantAssignmentJpaEntity::getPermissionSetId)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("findByOperatorId — 배정 없는 오퍼레이터 → 빈 목록 (NET-ZERO)")
    void findByOperatorId_noAssignments_returnsEmpty() {
        Long opId = saveOperator("op2@example.com");

        List<OperatorTenantAssignmentJpaEntity> result = repo.findByOperatorId(opId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("permission_set_id — admin_roles.id FK 로 저장/조회")
    void create_withPermissionSet_persistsFk() {
        Long opId = saveOperator("op3@example.com");
        Long roleId = insertRole("TEST_PERMSET_A");

        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opId, "finance", Instant.now(), opId, roleId));

        List<OperatorTenantAssignmentJpaEntity> result = repo.findByOperatorId(opId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTenantId()).isEqualTo("finance");
        assertThat(result.get(0).getPermissionSetId()).isEqualTo(roleId);
        assertThat(result.get(0).getGrantedBy()).isEqualTo(opId);
    }

    @Test
    @DisplayName("BE-338: org_scope JSON round-trip — null(미설정) + populated 배열")
    void orgScope_jsonRoundTrip() {
        Long opId = saveOperator("op4@example.com");

        // null org_scope (net-zero default) via the 5-arg overload.
        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opId, "wms", Instant.now(), null, null));
        // populated org_scope (subtree-root id array).
        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(
                opId, "scm", Instant.now(), null, null, List.of("dept-sales", "dept-ops")));

        OperatorTenantAssignmentJpaEntity wms =
                repo.findByOperatorIdAndTenantId(opId, "wms").orElseThrow();
        assertThat(wms.getOrgScope()).as("unset org_scope → null (net-zero)").isNull();

        OperatorTenantAssignmentJpaEntity scm =
                repo.findByOperatorIdAndTenantId(opId, "scm").orElseThrow();
        assertThat(scm.getOrgScope()).containsExactly("dept-sales", "dept-ops");
    }

    @Test
    @DisplayName("BE-338: org_scope [] (명시적 zero-scope) round-trip — NULL 과 구분")
    void orgScope_emptyArray_distinctFromNull() {
        Long opId = saveOperator("op5@example.com");

        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(
                opId, "finance", Instant.now(), null, null, List.of()));

        OperatorTenantAssignmentJpaEntity row =
                repo.findByOperatorIdAndTenantId(opId, "finance").orElseThrow();
        // Explicit empty array is preserved as [] (NOT widened to null/["*"]).
        assertThat(row.getOrgScope()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("다른 오퍼레이터의 배정은 제외")
    void findByOperatorId_excludesOtherOperators() {
        Long opA = saveOperator("opA@example.com");
        Long opB = saveOperator("opB@example.com");

        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opA, "wms", Instant.now(), null, null));
        repo.saveAndFlush(OperatorTenantAssignmentJpaEntity.create(opB, "scm", Instant.now(), null, null));

        List<OperatorTenantAssignmentJpaEntity> result = repo.findByOperatorId(opA);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTenantId()).isEqualTo("wms");
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
