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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AdminOperatorRefreshTokenJpaRepository 쿼리 슬라이스 테스트")
class AdminOperatorRefreshTokenJpaRepositoryTest {

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
    private AdminOperatorRefreshTokenJpaRepository repo;

    @Autowired
    private AdminOperatorJpaRepository operatorRepo;

    @BeforeEach
    void cleanup() {
        // ON DELETE CASCADE propagates to admin_operator_refresh_tokens
        operatorRepo.deleteAll();
    }

    // ── revokeAllForOperator ──────────────────────────────────────────────────

    @Test
    @DisplayName("revokeAllForOperator — 활성 토큰 전체 revoke, 갱신 건수 반환")
    void revokeAllForOperator_activeTokens_revokesAllAndReturnsCount() {
        Long opId = saveOperator("rt-a@example.com");
        repo.saveAndFlush(issueToken(opId));
        repo.saveAndFlush(issueToken(opId));

        Instant now = Instant.now();
        int count = repo.revokeAllForOperator(opId, now, AdminOperatorRefreshTokenJpaEntity.REASON_LOGOUT);

        assertThat(count).isEqualTo(2);
        List<AdminOperatorRefreshTokenJpaEntity> tokens = repo.findAll();
        assertThat(tokens).allSatisfy(t -> assertThat(t.isRevoked()).isTrue());
    }

    @Test
    @DisplayName("revokeAllForOperator — 이미 revoke된 토큰은 제외, 활성 토큰만 업데이트")
    void revokeAllForOperator_skipsAlreadyRevokedTokens() {
        Long opId = saveOperator("rt-b@example.com");
        AdminOperatorRefreshTokenJpaEntity active = issueToken(opId);
        AdminOperatorRefreshTokenJpaEntity alreadyRevoked = issueToken(opId);
        alreadyRevoked.revoke(Instant.now().minusSeconds(60), AdminOperatorRefreshTokenJpaEntity.REASON_LOGOUT);
        repo.saveAndFlush(active);
        repo.saveAndFlush(alreadyRevoked);

        int count = repo.revokeAllForOperator(
                opId, Instant.now(), AdminOperatorRefreshTokenJpaEntity.REASON_REUSE_DETECTED);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("revokeAllForOperator — 다른 operator의 토큰은 영향 없음")
    void revokeAllForOperator_differentOperator_notAffected() {
        Long opA = saveOperator("rt-c@example.com");
        Long opB = saveOperator("rt-d@example.com");
        AdminOperatorRefreshTokenJpaEntity tokenA = issueToken(opA);
        AdminOperatorRefreshTokenJpaEntity tokenB = issueToken(opB);
        repo.saveAndFlush(tokenA);
        repo.saveAndFlush(tokenB);

        repo.revokeAllForOperator(opA, Instant.now(), AdminOperatorRefreshTokenJpaEntity.REASON_FORCE_LOGOUT);

        AdminOperatorRefreshTokenJpaEntity reloadedB =
                repo.findById(tokenB.getJti()).orElseThrow();
        assertThat(reloadedB.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("revokeAllForOperator — 토큰이 없는 operator → 0 반환")
    void revokeAllForOperator_noTokens_returnsZero() {
        Long opId = saveOperator("rt-e@example.com");

        int count = repo.revokeAllForOperator(opId, Instant.now(), AdminOperatorRefreshTokenJpaEntity.REASON_LOGOUT);

        assertThat(count).isZero();
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

    private static AdminOperatorRefreshTokenJpaEntity issueToken(Long operatorId) {
        return AdminOperatorRefreshTokenJpaEntity.issue(
                UUID.randomUUID().toString(),
                operatorId,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null);
    }
}
