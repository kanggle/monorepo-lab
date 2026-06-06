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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminRoleJpaRepository 쿼리 슬라이스 테스트")
class AdminRoleJpaRepositoryTest {

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
    private AdminRoleJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        jdbc.update("DELETE FROM admin_roles WHERE name LIKE 'TEST_%'");
    }

    // ── findByName ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByName — 존재하는 role 이름 → role 반환")
    void findByName_existing_returnsRole() {
        insertRole("TEST_VIEWER");

        Optional<AdminRoleJpaEntity> result = repo.findByName("TEST_VIEWER");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("TEST_VIEWER");
    }

    @Test
    @DisplayName("findByName — 없는 role 이름 → empty")
    void findByName_unknown_returnsEmpty() {
        assertThat(repo.findByName("TEST_GHOST_ROLE")).isEmpty();
    }

    // ── findByNameIn ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByNameIn — 이름 목록으로 일괄 조회")
    void findByNameIn_existingNames_returnsMatchingRoles() {
        insertRole("TEST_EDITOR");
        insertRole("TEST_MANAGER");
        insertRole("TEST_OTHER");

        List<AdminRoleJpaEntity> result = repo.findByNameIn(List.of("TEST_EDITOR", "TEST_MANAGER"));

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AdminRoleJpaEntity::getName)
                .containsExactlyInAnyOrder("TEST_EDITOR", "TEST_MANAGER");
    }

    @Test
    @DisplayName("findByNameIn — 요청 목록에 없는 이름은 제외")
    void findByNameIn_partialMatch_excludesUnrequested() {
        insertRole("TEST_ROLE_X");
        insertRole("TEST_ROLE_Y");

        List<AdminRoleJpaEntity> result = repo.findByNameIn(List.of("TEST_ROLE_X"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("TEST_ROLE_X");
    }

    @Test
    @DisplayName("findByNameIn — 존재하지 않는 이름만 요청 → 빈 목록 반환")
    void findByNameIn_noMatch_returnsEmpty() {
        List<AdminRoleJpaEntity> result = repo.findByNameIn(List.of("TEST_NONEXISTENT"));

        assertThat(result).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void insertRole(String name) {
        jdbc.update(
                "INSERT INTO admin_roles (name, description, require_2fa, created_at) VALUES (?, ?, false, ?)",
                name, "Test role", java.sql.Timestamp.from(Instant.now()));
    }
}
