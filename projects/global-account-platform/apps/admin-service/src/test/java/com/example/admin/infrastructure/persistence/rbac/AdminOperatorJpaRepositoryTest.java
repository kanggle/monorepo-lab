package com.example.admin.infrastructure.persistence.rbac;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminOperatorJpaRepository 쿼리 슬라이스 테스트")
class AdminOperatorJpaRepositoryTest {

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
    private AdminOperatorJpaRepository repo;

    @BeforeEach
    void cleanup() {
        repo.deleteAll();
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail — 존재하는 이메일 → 오퍼레이터 반환")
    void findByEmail_existing_returnsOperator() {
        repo.saveAndFlush(operator("alice@example.com", "ACTIVE"));

        Optional<AdminOperatorJpaEntity> result = repo.findByEmail("alice@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("findByEmail — 없는 이메일 → empty")
    void findByEmail_unknown_returnsEmpty() {
        assertThat(repo.findByEmail("ghost@example.com")).isEmpty();
    }

    // ── findByOperatorId ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOperatorId — 존재하는 operatorId → 오퍼레이터 반환")
    void findByOperatorId_existing_returnsOperator() {
        AdminOperatorJpaEntity saved = repo.saveAndFlush(operator("bob@example.com", "ACTIVE"));
        String operatorId = saved.getOperatorId();

        Optional<AdminOperatorJpaEntity> result = repo.findByOperatorId(operatorId);

        assertThat(result).isPresent();
        assertThat(result.get().getOperatorId()).isEqualTo(operatorId);
    }

    @Test
    @DisplayName("findByOperatorId — 없는 operatorId → empty")
    void findByOperatorId_unknown_returnsEmpty() {
        assertThat(repo.findByOperatorId("ghost-uuid")).isEmpty();
    }

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("existsByEmail — 존재하는 이메일 → true")
    void existsByEmail_existing_returnsTrue() {
        repo.saveAndFlush(operator("carol@example.com", "ACTIVE"));

        assertThat(repo.existsByEmail("carol@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail — 없는 이메일 → false")
    void existsByEmail_unknown_returnsFalse() {
        assertThat(repo.existsByEmail("ghost@example.com")).isFalse();
    }

    // ── findByStatus ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByStatus — status 필터로 ACTIVE 오퍼레이터만 반환")
    void findByStatus_filter_returnsMatchingOnly() {
        repo.saveAndFlush(operator("op1@example.com", "ACTIVE"));
        repo.saveAndFlush(operator("op2@example.com", "ACTIVE"));
        repo.saveAndFlush(operator("op3@example.com", "ACTIVE"));
        repo.saveAndFlush(operator("op4@example.com", "SUSPENDED"));

        Page<AdminOperatorJpaEntity> page = repo.findByStatus("ACTIVE", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(AdminOperatorJpaEntity::getStatus)
                .containsOnly("ACTIVE");
    }

    @Test
    @DisplayName("findByStatus — 페이지네이션 크기 적용")
    void findByStatus_pagination_respectsPageSize() {
        for (int i = 0; i < 5; i++) {
            repo.saveAndFlush(operator("op" + i + "@example.com", "ACTIVE"));
        }

        Page<AdminOperatorJpaEntity> page0 = repo.findByStatus("ACTIVE", PageRequest.of(0, 2));
        Page<AdminOperatorJpaEntity> page1 = repo.findByStatus("ACTIVE", PageRequest.of(1, 2));

        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page0.getContent().get(0).getId())
                .isNotEqualTo(page1.getContent().get(0).getId());
    }

    @Test
    @DisplayName("findByStatus — 존재하지 않는 status → 빈 페이지 반환")
    void findByStatus_noMatch_returnsEmptyPage() {
        repo.saveAndFlush(operator("op@example.com", "ACTIVE"));

        Page<AdminOperatorJpaEntity> page = repo.findByStatus("DELETED", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AdminOperatorJpaEntity operator(String email, String status) {
        return AdminOperatorJpaEntity.create(
                AdminOperatorJpaEntity.newOperatorId(),
                email,
                "$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder.",
                "Test Op",
                status,
                Instant.now());
    }
}
