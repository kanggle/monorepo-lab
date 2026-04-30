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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminOperatorTotpJpaRepository 쿼리 슬라이스 테스트")
class AdminOperatorTotpJpaRepositoryTest {

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
    private AdminOperatorTotpJpaRepository repo;

    @Autowired
    private AdminOperatorJpaRepository operatorRepo;

    @BeforeEach
    void cleanup() {
        // admin_operator_totp FK cascades on operator delete
        operatorRepo.deleteAll();
    }

    // ── findByOperatorIdIn ────────────────────────────────────────────────────

    @Test
    @DisplayName("findByOperatorIdIn — TOTP 등록된 오퍼레이터 행 반환")
    void findByOperatorIdIn_enrolledOperators_returnsRows() {
        Long opA = saveOperator("totp-a@example.com");
        Long opB = saveOperator("totp-b@example.com");
        repo.saveAndFlush(totp(opA));
        repo.saveAndFlush(totp(opB));

        List<AdminOperatorTotpJpaEntity> result = repo.findByOperatorIdIn(List.of(opA, opB));

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(AdminOperatorTotpJpaEntity::getOperatorId)
                .containsExactlyInAnyOrder(opA, opB);
    }

    @Test
    @DisplayName("findByOperatorIdIn — TOTP 미등록 오퍼레이터는 제외")
    void findByOperatorIdIn_unenrolledOperator_excluded() {
        Long opEnrolled = saveOperator("totp-enrolled@example.com");
        Long opNotEnrolled = saveOperator("totp-none@example.com");
        repo.saveAndFlush(totp(opEnrolled));

        List<AdminOperatorTotpJpaEntity> result =
                repo.findByOperatorIdIn(List.of(opEnrolled, opNotEnrolled));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperatorId()).isEqualTo(opEnrolled);
    }

    @Test
    @DisplayName("findByOperatorIdIn — 요청 목록에 없는 오퍼레이터 TOTP는 반환하지 않음")
    void findByOperatorIdIn_excludesUnrequested() {
        Long opA = saveOperator("totp-req@example.com");
        Long opB = saveOperator("totp-unreq@example.com");
        repo.saveAndFlush(totp(opA));
        repo.saveAndFlush(totp(opB));

        List<AdminOperatorTotpJpaEntity> result = repo.findByOperatorIdIn(List.of(opA));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperatorId()).isEqualTo(opA);
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

    private static AdminOperatorTotpJpaEntity totp(Long operatorId) {
        return AdminOperatorTotpJpaEntity.create(
                operatorId,
                new byte[]{1, 2, 3, 4},
                "key-v1",
                "[\"hash1\",\"hash2\"]",
                Instant.now());
    }
}
