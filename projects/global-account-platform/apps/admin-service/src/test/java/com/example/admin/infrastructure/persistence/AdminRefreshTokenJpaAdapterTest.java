package com.example.admin.infrastructure.persistence;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-040-fix2 — regression test for the {@code @Modifying(clearAutomatically = true)}
 * applied to
 * {@link AdminOperatorRefreshTokenJpaRepository#revokeAllForOperator}. Verifies
 * that inside the same transaction a subsequent {@code findById} returns the
 * REVOKED state (the persistence context is flushed and cleared, so no stale
 * entity is served from L1 cache).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminRefreshTokenJpaAdapter.class)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminRefreshTokenJpaAdapter — clearAutomatically regression")
class AdminRefreshTokenJpaAdapterTest {

    @Container
    @SuppressWarnings("resource")
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

    @Autowired AdminRefreshTokenJpaAdapter adapter;
    @Autowired AdminOperatorRefreshTokenJpaRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    @DisplayName("동일 tx 에서 revokeAllForOperator 이후 findByJti 는 REVOKED row 를 반환한다")
    void revokeAllForOperator_clearsPersistenceContext() {
        // Arrange: insert one admin_operators row + two refresh-token rows for it.
        Long opPk = insertOperator();
        String jtiA = "aaaaaaaa-1111-1111-1111-000000000001";
        String jtiB = "aaaaaaaa-1111-1111-1111-000000000002";
        Instant issued = Instant.now().minusSeconds(60);
        Instant exp = issued.plusSeconds(3600);
        repository.saveAndFlush(AdminOperatorRefreshTokenJpaEntity.issue(jtiA, opPk, issued, exp, null));
        repository.saveAndFlush(AdminOperatorRefreshTokenJpaEntity.issue(jtiB, opPk, issued, exp, null));

        // Prime the persistence context — without clearAutomatically these two
        // managed entities would hide the bulk UPDATE on re-read.
        Optional<AdminRefreshTokenPortLike> preA = adapter.findByJti(jtiA).map(AdminRefreshTokenPortLike::from);
        assertThat(preA).isPresent();
        assertThat(preA.get().revoked).isFalse();

        // Act
        Instant at = Instant.now();
        int updated = adapter.revokeAllForOperator(opPk, at, "REUSE_DETECTED");
        assertThat(updated).isEqualTo(2);

        // Assert: same transaction, findByJti now reflects the revoked state
        // because the repository method declared clearAutomatically = true.
        AdminRefreshTokenPortLike postA = adapter.findByJti(jtiA).map(AdminRefreshTokenPortLike::from).orElseThrow();
        AdminRefreshTokenPortLike postB = adapter.findByJti(jtiB).map(AdminRefreshTokenPortLike::from).orElseThrow();
        assertThat(postA.revoked).isTrue();
        assertThat(postA.revokeReason).isEqualTo("REUSE_DETECTED");
        assertThat(postB.revoked).isTrue();
        assertThat(postB.revokeReason).isEqualTo("REUSE_DETECTED");
    }

    private Long insertOperator() {
        String operatorUuid = AdminOperatorJpaEntity.newOperatorId();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO admin_operators " +
                        "(operator_id, email, password_hash, display_name, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                operatorUuid,
                "op-" + operatorUuid + "@example.com",
                "$2a$10$placeholderplaceholderplaceholderplaceholderplaceholder",
                "Op " + operatorUuid.substring(0, 8),
                "ACTIVE",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        return jdbc.queryForObject(
                "SELECT id FROM admin_operators WHERE operator_id = ?",
                Long.class, operatorUuid);
    }

    /**
     * Local projection over {@link com.example.admin.application.port.AdminRefreshTokenPort.TokenRecord}
     * so the test asserts on named fields without importing the port record
     * directly (the adapter already translates to it, we just need the revoked
     * flag + reason for the assertion).
     */
    private record AdminRefreshTokenPortLike(boolean revoked, String revokeReason) {
        static AdminRefreshTokenPortLike from(
                com.example.admin.application.port.AdminRefreshTokenPort.TokenRecord r) {
            return new AdminRefreshTokenPortLike(r.isRevoked(), r.revokeReason());
        }
    }
}
