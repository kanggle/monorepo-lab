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

    // TASK-MONO-263 (ADR-032 D5 step 4): the account_type column is dropped (V0025)
    // — the account_type round-trip slice tests (TASK-BE-329) are deleted. The
    // V0025 Flyway DROP + JPA validate is exercised by the CI iam Testcontainers IT.

    // ── TASK-BE-378 (ADR-MONO-035 O3): credentials.identity_id additive net-zero ──

    @Test
    @DisplayName("BE-378: credentials.identity_id 컬럼은 VARCHAR(36) NULLABLE (V0026)")
    void identityIdColumn_isNullableVarchar36() {
        // The migration ran (this @DataJpaTest boots Flyway); assert the additive column's
        // shape via INFORMATION_SCHEMA. NULLABLE + VARCHAR(36) value-convention cross-DB ref.
        String nullable = jdbc.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'credentials' AND COLUMN_NAME = 'identity_id'",
                String.class);
        assertThat(nullable).as("identity_id must be NULLABLE (additive net-zero)").isEqualTo("YES");

        Long maxLen = jdbc.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE TABLE_NAME = 'credentials' AND COLUMN_NAME = 'identity_id'",
                Long.class);
        assertThat(maxLen).as("identity_id must be VARCHAR(36)").isEqualTo(36L);
    }

    @Test
    @DisplayName("BE-378: credential 은 identity_id 없이 INSERT 되어도(NULL) 정상 — 미매핑·net-zero")
    void credential_insertsWithNullIdentityId_andIsReadable() {
        // The standard insert path does not set identity_id → it stays NULL (no
        // creation path is wired to it). The entity (which does NOT map identity_id)
        // still round-trips the row, proving ddl-auto=validate tolerates the extra column.
        String accountId = uuid();
        insertCredential(accountId, null);

        assertThat(repo.findByAccountId(accountId)).isPresent();
        String identityId = jdbc.queryForObject(
                "SELECT identity_id FROM credentials WHERE account_id = ?", String.class, accountId);
        assertThat(identityId).as("new credential carries NULL identity_id (no creation path wired)").isNull();

        // An externally-populated identity_id (the deferred consolidation backfill) is
        // preserved — the unmapped column is never nulled by a JPA credential update.
        String idy = uuid();
        jdbc.update("UPDATE credentials SET identity_id = ? WHERE account_id = ?", idy, accountId);
        String readBack = jdbc.queryForObject(
                "SELECT identity_id FROM credentials WHERE account_id = ?", String.class, accountId);
        assertThat(readBack).isEqualTo(idy);
    }

    // ── TASK-BE-384 (ADR-MONO-036 M2): born-unified identity_id native WRITER ─────

    @Test
    @DisplayName("BE-384: assignIdentityIdIfAbsent — NULL 이면 할당(1행), 이미 set 이면 no-op(0행, 덮어쓰기 없음)")
    void assignIdentityIdIfAbsent_setsWhenNull_idempotentNoOverwrite() {
        String accountId = uuid();
        insertCredential(accountId, null); // identity_id NULL at birth

        String idy1 = uuid();
        String idy2 = uuid();
        // first assign sets it (1 row affected)
        assertThat(repo.assignIdentityIdIfAbsent(accountId, idy1)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT identity_id FROM credentials WHERE account_id = ?", String.class, accountId))
                .isEqualTo(idy1);

        // second assign with a DIFFERENT identity → no-op (0 rows), original preserved (no silent re-link)
        assertThat(repo.assignIdentityIdIfAbsent(accountId, idy2)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT identity_id FROM credentials WHERE account_id = ?", String.class, accountId))
                .isEqualTo(idy1);
    }

    @Test
    @DisplayName("BE-384: assignIdentityIdIfAbsent — 없는 account 는 0행 (net-zero)")
    void assignIdentityIdIfAbsent_missingAccount_zeroRows() {
        assertThat(repo.assignIdentityIdIfAbsent("ghost-account", uuid())).isZero();
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
