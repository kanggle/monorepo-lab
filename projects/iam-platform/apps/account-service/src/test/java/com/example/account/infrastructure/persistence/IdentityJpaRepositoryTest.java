package com.example.account.infrastructure.persistence;

import com.example.account.domain.identity.Identity;
import com.example.account.domain.identity.IdentityStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema + repository slice for the central identities registry (ADR-MONO-034
 * step 3a, V0023). Testcontainers MySQL runs the real Flyway migrations, so this
 * is the authoritative verification that V0023 applies, the FK holds, and the
 * backfill SQL produces a 1:1 account→identity mapping. Skipped locally when
 * Docker is unavailable ({@link DockerAvailableCondition}); CI is the authority.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("IdentityJpaRepository + V0023 identities 레지스트리/backfill 슬라이스 테스트")
class IdentityJpaRepositoryTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("account_db")
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
    private IdentityJpaRepository identityRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String TENANT_FAN = "fan-platform";
    private static final String TENANT_WMS = "wms";

    @BeforeEach
    void cleanup() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM identities");
        jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", TENANT_WMS);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }

    private void insertWmsTenant() {
        jdbc.update(
                "INSERT INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at) " +
                "VALUES (?, 'WMS', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6))",
                TENANT_WMS);
    }

    private void insertAccount(String tenantId, String id, String email) {
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)",
                id, tenantId, email, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }

    // ── entity / repository CRUD ─────────────────────────────────────────────

    @Test
    @DisplayName("save + findById — 라운드트립")
    void saveAndFindById() {
        Identity identity = Identity.create(TenantId.FAN_PLATFORM, "person@example.com");
        identityRepo.save(IdentityJpaEntity.fromDomain(identity));

        Optional<IdentityJpaEntity> found = identityRepo.findById(identity.getIdentityId());

        assertThat(found).isPresent();
        assertThat(found.get().getPrimaryEmail()).isEqualTo("person@example.com");
        assertThat(found.get().getStatus()).isEqualTo(IdentityStatus.ACTIVE);
        assertThat(found.get().getTenantId()).isEqualTo(TENANT_FAN);
    }

    @Test
    @DisplayName("findByTenantIdAndPrimaryEmail — cross-tenant 격리 (같은 이메일 다른 테넌트)")
    void findByTenantAndEmail_isolatesCrossTenant() {
        insertWmsTenant();
        String email = "shared@example.com";
        Identity fan = Identity.create(TenantId.FAN_PLATFORM, email);
        Identity wms = Identity.create(new TenantId(TENANT_WMS), email);
        identityRepo.save(IdentityJpaEntity.fromDomain(fan));
        identityRepo.save(IdentityJpaEntity.fromDomain(wms));

        Optional<IdentityJpaEntity> fanResult = identityRepo.findByTenantIdAndPrimaryEmail(TENANT_FAN, email);
        Optional<IdentityJpaEntity> wmsResult = identityRepo.findByTenantIdAndPrimaryEmail(TENANT_WMS, email);

        assertThat(fanResult).isPresent();
        assertThat(fanResult.get().getIdentityId()).isEqualTo(fan.getIdentityId());
        assertThat(wmsResult).isPresent();
        assertThat(wmsResult.get().getIdentityId()).isEqualTo(wms.getIdentityId());
    }

    @Test
    @DisplayName("uk_identities_tenant_email — 같은 (테넌트,이메일) 중복 insert → 거부")
    void uniqueTenantEmail_enforced() {
        String email = "dup@example.com";
        identityRepo.save(IdentityJpaEntity.fromDomain(Identity.create(TenantId.FAN_PLATFORM, email)));
        identityRepo.flush();

        assertThatThrownBy(() -> {
            identityRepo.save(IdentityJpaEntity.fromDomain(Identity.create(TenantId.FAN_PLATFORM, email)));
            identityRepo.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── accounts.identity_id FK (fk_accounts_identity_id) ────────────────────

    @Test
    @DisplayName("FK accounts.identity_id → identities — 존재하지 않는 identity_id 참조 시 거부")
    void accountIdentityFk_rejectsDanglingReference() {
        String accountId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)",
                accountId, "non-existent-identity", TENANT_FAN, "fk@example.com"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("FK accounts.identity_id → identities — NULL 허용 (신규 계정 미배선 윈도우)")
    void accountIdentityFk_allowsNull() {
        // ADR-034 step 3a: identity_id is nullable; a new account created before
        // step 3d wires provisioning carries NULL and the FK permits it.
        String accountId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, NULL, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)",
                accountId, TENANT_FAN, "nullid@example.com");

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE id = ? AND identity_id IS NULL", Integer.class, accountId);
        assertThat(cnt).isEqualTo(1);
    }

    // ── backfill SQL logic (V0023) — replayed on test-inserted accounts ──────

    @Test
    @DisplayName("backfill SQL — 계정당 1개 fresh identity 생성 + identity_id 링크 (1:1)")
    void backfillSql_producesOneIdentityPerAccount() {
        insertWmsTenant();
        String fanId = UUID.randomUUID().toString();
        String wmsId = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, fanId, "bf-fan@example.com");
        insertAccount(TENANT_WMS, wmsId, "bf-wms@example.com");

        // Replay the exact V0023 backfill statements (the migration itself ran at
        // container start against empty tables, so re-run the logic here against
        // these test rows to assert the SQL is correct).
        jdbc.update(
                "INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) " +
                "SELECT UUID(), a.tenant_id, a.email, 'ACTIVE', a.created_at, a.updated_at, 0 " +
                "FROM accounts a WHERE a.identity_id IS NULL");
        jdbc.update(
                "UPDATE accounts a JOIN identities i " +
                "  ON i.tenant_id = a.tenant_id AND i.primary_email = a.email " +
                "SET a.identity_id = i.identity_id WHERE a.identity_id IS NULL");

        // one identity per account
        Integer identityCount = jdbc.queryForObject("SELECT COUNT(*) FROM identities", Integer.class);
        assertThat(identityCount).isEqualTo(2);

        // each account is linked to an identity whose (tenant,email) matches, and
        // identity_id is a NEW UUID (NOT the account id) per U1-A.
        String fanIdentityId = jdbc.queryForObject(
                "SELECT identity_id FROM accounts WHERE id = ?", String.class, fanId);
        assertThat(fanIdentityId).isNotNull().isNotEqualTo(fanId);
        String fanIdentityEmail = jdbc.queryForObject(
                "SELECT primary_email FROM identities WHERE identity_id = ?", String.class, fanIdentityId);
        assertThat(fanIdentityEmail).isEqualTo("bf-fan@example.com");

        // 1:1 — no identity is shared by two accounts
        List<String> linkedIdentityIds = jdbc.queryForList(
                "SELECT identity_id FROM accounts WHERE identity_id IS NOT NULL", String.class);
        assertThat(linkedIdentityIds).doesNotHaveDuplicates().hasSize(2);
    }
}
