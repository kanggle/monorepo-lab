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
@DisplayName("AdminRolePermissionJpaRepository 쿼리 슬라이스 테스트")
class AdminRolePermissionJpaRepositoryTest {

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
    private AdminRolePermissionJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        // admin_role_permissions CASCADE from admin_roles, so delete test roles to clean permissions
        jdbc.update("DELETE FROM admin_roles WHERE name LIKE 'TEST_%'");
    }

    // ── findPermissionKeysByRoleIds ───────────────────────────────────────────

    @Test
    @DisplayName("findPermissionKeysByRoleIds — 단일 role의 권한 키 목록 반환")
    void findPermissionKeysByRoleIds_singleRole_returnsKeys() {
        Long roleId = insertRole("TEST_ROLE_PERM_A");
        insertPermission(roleId, "account:read");
        insertPermission(roleId, "account:lock");

        List<String> keys = repo.findPermissionKeysByRoleIds(List.of(roleId));

        assertThat(keys).containsExactlyInAnyOrder("account:read", "account:lock");
    }

    @Test
    @DisplayName("findPermissionKeysByRoleIds — 복수 role의 권한 키 합산 반환")
    void findPermissionKeysByRoleIds_multipleRoles_returnsAllKeys() {
        Long roleA = insertRole("TEST_ROLE_PERM_B");
        Long roleB = insertRole("TEST_ROLE_PERM_C");
        insertPermission(roleA, "account:read");
        insertPermission(roleB, "account:lock");
        insertPermission(roleB, "operator:manage");

        List<String> keys = repo.findPermissionKeysByRoleIds(List.of(roleA, roleB));

        assertThat(keys).containsExactlyInAnyOrder("account:read", "account:lock", "operator:manage");
    }

    @Test
    @DisplayName("findPermissionKeysByRoleIds — 권한 없는 role → 빈 목록 반환")
    void findPermissionKeysByRoleIds_roleWithNoPermissions_returnsEmpty() {
        Long roleId = insertRole("TEST_ROLE_PERM_D");

        List<String> keys = repo.findPermissionKeysByRoleIds(List.of(roleId));

        assertThat(keys).isEmpty();
    }

    @Test
    @DisplayName("findPermissionKeysByRoleIds — 다른 role의 권한은 포함하지 않음")
    void findPermissionKeysByRoleIds_excludesOtherRoles() {
        Long roleA = insertRole("TEST_ROLE_PERM_E");
        Long roleB = insertRole("TEST_ROLE_PERM_F");
        insertPermission(roleA, "account:read");
        insertPermission(roleB, "account:lock");

        List<String> keys = repo.findPermissionKeysByRoleIds(List.of(roleA));

        assertThat(keys).containsExactly("account:read");
        assertThat(keys).doesNotContain("account:lock");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Long insertRole(String name) {
        jdbc.update(
                "INSERT INTO admin_roles (name, description, require_2fa, created_at) VALUES (?, ?, false, ?)",
                name, "Test role", java.sql.Timestamp.from(Instant.now()));
        return jdbc.queryForObject("SELECT id FROM admin_roles WHERE name = ?", Long.class, name);
    }

    private void insertPermission(Long roleId, String permissionKey) {
        jdbc.update(
                "INSERT INTO admin_role_permissions (role_id, permission_key) VALUES (?, ?)",
                roleId, permissionKey);
    }
}
