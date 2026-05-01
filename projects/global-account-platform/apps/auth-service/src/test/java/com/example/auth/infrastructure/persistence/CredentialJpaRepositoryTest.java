package com.example.auth.infrastructure.persistence;

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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("CredentialJpaRepository 쿼리 슬라이스 테스트")
class CredentialJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("auth_db")
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
    private CredentialJpaRepository repo;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanup() {
        jdbc.update("DELETE FROM credentials");
    }

    // ── findByAccountId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByAccountId — 존재하는 accountId → credential 반환")
    void findByAccountId_existing_returnsCredential() {
        String accountId = uuid();
        insertCredential(accountId, null);

        Optional<CredentialJpaEntity> result = repo.findByAccountId(accountId);

        assertThat(result).isPresent();
        assertThat(result.get().getAccountId()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("findByAccountId — 없는 accountId → empty")
    void findByAccountId_unknown_returnsEmpty() {
        assertThat(repo.findByAccountId(uuid())).isEmpty();
    }

    // ── findByEmail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("findByEmail — 이메일이 등록된 credential → 반환")
    void findByEmail_existing_returnsCredential() {
        String accountId = uuid();
        String email = uuid() + "@example.com";
        insertCredential(accountId, email);

        Optional<CredentialJpaEntity> result = repo.findByEmail(email);

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("findByEmail — 없는 이메일 → empty")
    void findByEmail_unknown_returnsEmpty() {
        assertThat(repo.findByEmail("ghost@example.com")).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void insertCredential(String accountId, String email) {
        jdbc.update(
                "INSERT INTO credentials (account_id, tenant_id, email, credential_hash, hash_algorithm, created_at, updated_at, version)" +
                " VALUES (?, 'fan-platform', ?, ?, ?, NOW(6), NOW(6), 0)",
                accountId, email, "$argon2id$placeholder", "argon2id");
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }
}
