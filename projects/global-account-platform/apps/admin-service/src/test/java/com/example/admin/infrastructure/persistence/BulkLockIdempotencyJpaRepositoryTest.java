package com.example.admin.infrastructure.persistence;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("BulkLockIdempotencyJpaRepository 쿼리 슬라이스 테스트")
class BulkLockIdempotencyJpaRepositoryTest {

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
    private BulkLockIdempotencyJpaRepository repo;

    @Autowired
    private AdminOperatorJpaRepository operatorRepo;

    @BeforeEach
    void cleanup() {
        // ON DELETE CASCADE propagates to admin_bulk_lock_idempotency
        operatorRepo.deleteAll();
    }

    // ── findById (composite key) ──────────────────────────────────────────────

    @Test
    @DisplayName("findById — 저장된 복합키 → 엔티티 반환")
    void findById_existingKey_returnsEntity() {
        Long opId = saveOperator("idemp-a@example.com");
        repo.saveAndFlush(BulkLockIdempotencyJpaEntity.create(
                opId, "key-001", "hash-abc", "{}", Instant.now()));

        Optional<BulkLockIdempotencyJpaEntity> result =
                repo.findById(new BulkLockIdempotencyJpaEntity.Key(opId, "key-001"));

        assertThat(result).isPresent();
        assertThat(result.get().getId().getOperatorId()).isEqualTo(opId);
        assertThat(result.get().getId().getIdempotencyKey()).isEqualTo("key-001");
    }

    @Test
    @DisplayName("findById — 없는 복합키 → empty")
    void findById_notFound_returnsEmpty() {
        Long opId = saveOperator("idemp-b@example.com");

        Optional<BulkLockIdempotencyJpaEntity> result =
                repo.findById(new BulkLockIdempotencyJpaEntity.Key(opId, "nonexistent-key"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findById — 같은 operator, 다른 idempotencyKey → 별개 행")
    void findById_sameOperatorDifferentKey_returnsEmpty() {
        Long opId = saveOperator("idemp-c@example.com");
        repo.saveAndFlush(BulkLockIdempotencyJpaEntity.create(
                opId, "key-A", "hash-x", "{}", Instant.now()));

        assertThat(repo.findById(new BulkLockIdempotencyJpaEntity.Key(opId, "key-B"))).isEmpty();
    }

    @Test
    @DisplayName("findById — 다른 operator, 같은 idempotencyKey → 별개 행")
    void findById_differentOperatorSameKey_returnsEmpty() {
        Long opA = saveOperator("idemp-d@example.com");
        Long opB = saveOperator("idemp-e@example.com");
        repo.saveAndFlush(BulkLockIdempotencyJpaEntity.create(
                opA, "shared-key", "hash-y", "{}", Instant.now()));

        assertThat(repo.findById(new BulkLockIdempotencyJpaEntity.Key(opB, "shared-key"))).isEmpty();
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
}
