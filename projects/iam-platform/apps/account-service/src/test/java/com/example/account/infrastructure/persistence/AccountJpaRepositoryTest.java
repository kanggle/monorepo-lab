package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("AccountJpaRepository 쿼리 슬라이스 테스트")
class AccountJpaRepositoryTest {

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
    private AccountJpaRepository accountRepo;

    @Autowired
    private ProfileJpaRepository profileRepo;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String TENANT_FAN = "fan-platform";
    private static final String TENANT_WMS = "wms";

    @BeforeEach
    void cleanup() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.update("DELETE FROM profiles");
        jdbc.update("DELETE FROM accounts");
        jdbc.update("DELETE FROM identities"); // TASK-BE-372: identity_id resolve tests insert identities
        // Ensure wms tenant exists for cross-tenant tests (fan-platform is seeded by V0009)
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
                id, tenantId, email,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    // TASK-BE-475: insert with an explicit lifecycle status for the status-filter tests.
    private void insertAccountWithStatus(String tenantId, String id, String email, String status) {
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0)",
                id, tenantId, email, status,
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now())
        );
    }

    // ── findByTenantIdAndEmail / existsByTenantIdAndEmail ────────────────────

    @Test
    @DisplayName("findByTenantIdAndEmail — 존재하는 이메일 → 계정 반환")
    void findByTenantIdAndEmail_existing_returnsAccount() {
        Account account = Account.create(TenantId.FAN_PLATFORM, "test@example.com");
        accountRepo.save(AccountJpaEntity.fromDomain(account));

        Optional<AccountJpaEntity> result = accountRepo.findByTenantIdAndEmail(TENANT_FAN, "test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
        assertThat(result.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ── TASK-BE-372 (ADR-MONO-034 step 3b): identity_id native resolve ───────

    @Test
    @DisplayName("findIdentityIdByTenantIdAndId — identity 링크된 계정 → identity_id 반환 (native projection)")
    void findIdentityId_linkedAccount_returnsIdentityId() {
        // an identity must exist for the FK (fk_accounts_identity_id, V0023)
        String identityId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)",
                identityId, TENANT_FAN, "linked@example.com");
        String accountId = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)",
                accountId, identityId, TENANT_FAN, "linked@example.com");

        Optional<String> result = accountRepo.findIdentityIdByTenantIdAndId(TENANT_FAN, accountId);

        assertThat(result).contains(identityId);
    }

    @Test
    @DisplayName("findIdentityIdByTenantIdAndId — 없는 계정 / cross-tenant → empty (enumeration-safe)")
    void findIdentityId_missingOrCrossTenant_returnsEmpty() {
        insertWmsTenant();
        String wmsAccountId = UUID.randomUUID().toString();
        insertAccount(TENANT_WMS, wmsAccountId, "wms-id@example.com"); // identity_id NULL

        // missing account
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_FAN, "ghost")).isEmpty();
        // cross-tenant (wms account looked up under fan) → empty
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_FAN, wmsAccountId)).isEmpty();
        // existing account but identity_id NULL (new-account window) → empty
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_WMS, wmsAccountId)).isEmpty();
    }

    // ── TASK-BE-381 (ADR-MONO-036 M1): born-unified identity_id native WRITER ─────

    @Test
    @DisplayName("assignIdentityIdIfAbsent — identity_id 가 NULL 인 계정에 할당(1행), 이미 set 이면 no-op(0행, 덮어쓰기 없음)")
    void assignIdentityIdIfAbsent_setsWhenNull_idempotentNoOverwrite() {
        // two identities exist (FK fk_accounts_identity_id, V0023)
        String idy1 = UUID.randomUUID().toString();
        String idy2 = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)", idy1, TENANT_FAN, "born@example.com");
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)", idy2, TENANT_FAN, "other@example.com");
        String accountId = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, accountId, "born@example.com"); // identity_id NULL at birth

        // first assign sets it (1 row affected)
        assertThat(accountRepo.assignIdentityIdIfAbsent(TENANT_FAN, accountId, idy1)).isEqualTo(1);
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_FAN, accountId)).contains(idy1);

        // second assign with a DIFFERENT identity → no-op (0 rows), original preserved (no silent re-link)
        assertThat(accountRepo.assignIdentityIdIfAbsent(TENANT_FAN, accountId, idy2)).isZero();
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_FAN, accountId)).contains(idy1);
    }

    @Test
    @DisplayName("assignIdentityIdIfAbsent — cross-tenant / 없는 계정엔 0행 (tenant-scoped)")
    void assignIdentityIdIfAbsent_crossTenantOrMissing_zeroRows() {
        insertWmsTenant();
        String idy = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)", idy, TENANT_WMS, "wms-born@example.com");
        String wmsAccountId = UUID.randomUUID().toString();
        insertAccount(TENANT_WMS, wmsAccountId, "wms-born@example.com");

        // wrong tenant → 0 rows (tenant-scoped WHERE), and missing account → 0 rows
        assertThat(accountRepo.assignIdentityIdIfAbsent(TENANT_FAN, wmsAccountId, idy)).isZero();
        assertThat(accountRepo.assignIdentityIdIfAbsent(TENANT_WMS, "ghost", idy)).isZero();
        // the wms account remains unlinked (cross-tenant attempt did not touch it)
        assertThat(accountRepo.findIdentityIdByTenantIdAndId(TENANT_WMS, wmsAccountId)).isEmpty();
    }

    @Test
    @DisplayName("existsByTenantIdAndEmail — 존재하는 이메일 → true")
    void existsByTenantIdAndEmail_existingEmail_returnsTrue() {
        Account account = Account.create(TenantId.FAN_PLATFORM, "exists@example.com");
        accountRepo.save(AccountJpaEntity.fromDomain(account));

        assertThat(accountRepo.existsByTenantIdAndEmail(TENANT_FAN, "exists@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByTenantIdAndEmail — 없는 이메일 → false")
    void existsByTenantIdAndEmail_unknownEmail_returnsFalse() {
        assertThat(accountRepo.existsByTenantIdAndEmail(TENANT_FAN, "ghost@example.com")).isFalse();
    }

    // ── cross-tenant leak regression ─────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant isolation — fan-platform 테넌트로 조회 시 wms 테넌트 row가 포함되지 않는다")
    void findByTenantIdAndEmail_isolatesCrossTenantRows() {
        insertWmsTenant();

        String sharedEmail = "shared@example.com";
        String fanId = UUID.randomUUID().toString();
        String wmsId = UUID.randomUUID().toString();

        insertAccount(TENANT_FAN, fanId, sharedEmail);
        insertAccount(TENANT_WMS, wmsId, sharedEmail);

        // fan-platform 조회 → fan-platform 계정만 반환
        Optional<AccountJpaEntity> fanResult = accountRepo.findByTenantIdAndEmail(TENANT_FAN, sharedEmail);
        assertThat(fanResult).isPresent();
        assertThat(fanResult.get().getId()).isEqualTo(fanId);
        assertThat(fanResult.get().getTenantId()).isEqualTo(TENANT_FAN);

        // wms 조회 → wms 계정만 반환
        Optional<AccountJpaEntity> wmsResult = accountRepo.findByTenantIdAndEmail(TENANT_WMS, sharedEmail);
        assertThat(wmsResult).isPresent();
        assertThat(wmsResult.get().getId()).isEqualTo(wmsId);
        assertThat(wmsResult.get().getTenantId()).isEqualTo(TENANT_WMS);
    }

    @Test
    @DisplayName("cross-tenant isolation — fan-platform tenant_id 로 wms account id를 조회하면 빈 결과")
    void findByTenantIdAndId_returnEmptyForCrossTenantId() {
        insertWmsTenant();

        String wmsId = UUID.randomUUID().toString();
        insertAccount(TENANT_WMS, wmsId, "wms-only@example.com");

        // fan-platform으로 wms account ID 조회 → empty
        Optional<AccountJpaEntity> result = accountRepo.findByTenantIdAndId(TENANT_FAN, wmsId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cross-tenant isolation — existsByTenantIdAndEmail은 해당 테넌트 내에서만 확인")
    void existsByTenantIdAndEmail_doesNotLeakAcrossTenants() {
        insertWmsTenant();

        String sharedEmail = "tenant-check@example.com";
        insertAccount(TENANT_WMS, UUID.randomUUID().toString(), sharedEmail);

        // fan-platform 테넌트에서는 이 이메일이 없어야 함
        assertThat(accountRepo.existsByTenantIdAndEmail(TENANT_FAN, sharedEmail)).isFalse();
        // wms 테넌트에서는 존재해야 함
        assertThat(accountRepo.existsByTenantIdAndEmail(TENANT_WMS, sharedEmail)).isTrue();
    }

    // ── TASK-BE-357: tenant-scoped search/list (AC-1 email, AC-2 list) ───────────

    @Test
    @DisplayName("TASK-BE-357: findByEmail(email) — '*' 크로스테넌트, 모든 테넌트의 동일 이메일 행을 반환")
    void findByEmail_starCrossTenant_returnsAllTenantsForSharedEmail() {
        insertWmsTenant();
        String sharedEmail = "dup@example.com";
        String fanId = UUID.randomUUID().toString();
        String wmsId = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, fanId, sharedEmail);
        insertAccount(TENANT_WMS, wmsId, sharedEmail);

        List<AccountJpaEntity> all = accountRepo.findByEmail(sharedEmail);

        // SUPER_ADMIN tenantId='*' email path — both tenants' rows surface (the same
        // email is NOT globally unique, only (tenant_id, email) is).
        assertThat(all).extracting(AccountJpaEntity::getId)
                .containsExactlyInAnyOrder(fanId, wmsId);
    }

    @Test
    @DisplayName("TASK-BE-357: findByTenantIdWithStatusFilter(tenantId, null) — 해당 테넌트 행만 (list 격리, AC-2)")
    void findByTenantIdWithStatusFilter_nullStatus_isolatesTenant() {
        insertWmsTenant();
        String fanId = UUID.randomUUID().toString();
        String wmsId = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, fanId, "fan-list@example.com");
        insertAccount(TENANT_WMS, wmsId, "wms-list@example.com");

        Page<AccountJpaEntity> fanPage = accountRepo.findByTenantIdWithStatusFilter(
                TENANT_FAN, null, PageRequest.of(0, 20));

        // The list path (status=null → all statuses) returns ONLY the queried tenant's
        // rows — a tenant-A operator never pages tenant-B accounts (was unscoped pre-357).
        assertThat(fanPage.getContent()).extracting(AccountJpaEntity::getId)
                .contains(fanId).doesNotContain(wmsId);
        assertThat(fanPage.getContent()).allMatch(e -> TENANT_FAN.equals(e.getTenantId()));
    }

    // ── TASK-BE-475: status-filtered list ────────────────────────────────────

    @Test
    @DisplayName("TASK-BE-475: findByTenantIdWithStatusFilter(tenantId, LOCKED) — 해당 테넌트의 LOCKED 계정만 (AC-1)")
    void findByTenantIdWithStatusFilter_lockedStatus_returnsOnlyLocked() {
        String activeId = UUID.randomUUID().toString();
        String lockedId = UUID.randomUUID().toString();
        insertAccountWithStatus(TENANT_FAN, activeId, "active@example.com", "ACTIVE");
        insertAccountWithStatus(TENANT_FAN, lockedId, "locked@example.com", "LOCKED");

        Page<AccountJpaEntity> lockedPage = accountRepo.findByTenantIdWithStatusFilter(
                TENANT_FAN, AccountStatus.LOCKED, PageRequest.of(0, 20));

        assertThat(lockedPage.getContent()).extracting(AccountJpaEntity::getId)
                .containsExactly(lockedId);
        assertThat(lockedPage.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("TASK-BE-475: findAllAccountsWithStatusFilter — '*' 전 테넌트 status 필터 (AC-4), null=전체")
    void findAllAccountsWithStatusFilter_starScope_filtersAcrossTenants() {
        insertWmsTenant();
        String fanLocked = UUID.randomUUID().toString();
        String wmsLocked = UUID.randomUUID().toString();
        String fanActive = UUID.randomUUID().toString();
        insertAccountWithStatus(TENANT_FAN, fanLocked, "fl@example.com", "LOCKED");
        insertAccountWithStatus(TENANT_WMS, wmsLocked, "wl@example.com", "LOCKED");
        insertAccountWithStatus(TENANT_FAN, fanActive, "fa@example.com", "ACTIVE");

        Page<AccountJpaEntity> locked = accountRepo.findAllAccountsWithStatusFilter(
                AccountStatus.LOCKED, PageRequest.of(0, 20));
        // "*" all-tenant LOCKED spans tenants but excludes ACTIVE.
        assertThat(locked.getContent()).extracting(AccountJpaEntity::getId)
                .containsExactlyInAnyOrder(fanLocked, wmsLocked);

        Page<AccountJpaEntity> all = accountRepo.findAllAccountsWithStatusFilter(
                null, PageRequest.of(0, 20));
        // null status → all statuses across all tenants (equivalent to findAllAccounts).
        assertThat(all.getContent()).extracting(AccountJpaEntity::getId)
                .contains(fanLocked, wmsLocked, fanActive);
    }

    // ── findActiveDormantCandidates ─────────────────────────────────────────

    @Test
    @DisplayName("findActiveDormantCandidates — lastLoginSucceededAt < threshold → 반환")
    void findActiveDormantCandidates_pastThreshold_returnsActiveAccounts() {
        Instant threshold = Instant.now().minus(365, ChronoUnit.DAYS);
        String id = UUID.randomUUID().toString();

        // ACTIVE, lastLoginSucceededAt 2년 전
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, last_login_succeeded_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, 0)",
                id, TENANT_FAN, id + "@ex.com",
                Timestamp.from(threshold.minus(365, ChronoUnit.DAYS)),
                Timestamp.from(threshold.minus(365, ChronoUnit.DAYS)),
                Timestamp.from(threshold.minus(1, ChronoUnit.DAYS))
        );

        List<AccountJpaEntity> candidates = accountRepo.findActiveDormantCandidates(threshold);

        assertThat(candidates).extracting(AccountJpaEntity::getId).contains(id);
    }

    @Test
    @DisplayName("findActiveDormantCandidates — 최근 로그인 계정 제외")
    void findActiveDormantCandidates_recentLogin_excludes() {
        Instant threshold = Instant.now().minus(365, ChronoUnit.DAYS);
        String id = UUID.randomUUID().toString();

        // lastLoginSucceededAt = 1일 전 (threshold 이후)
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, last_login_succeeded_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, 0)",
                id, TENANT_FAN, id + "@ex.com",
                Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))
        );

        List<AccountJpaEntity> candidates = accountRepo.findActiveDormantCandidates(threshold);

        assertThat(candidates).extracting(AccountJpaEntity::getId).doesNotContain(id);
    }

    @Test
    @DisplayName("findActiveDormantCandidates — lastLoginSucceededAt=null → createdAt 사용 (COALESCE)")
    void findActiveDormantCandidates_nullLastLogin_usesCreatedAt() {
        Instant threshold = Instant.now().minus(365, ChronoUnit.DAYS);
        String id = UUID.randomUUID().toString();

        // lastLoginSucceededAt=null, createdAt 2년 전
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, version) " +
                "VALUES (?, ?, ?, 'ACTIVE', ?, ?, 0)",
                id, TENANT_FAN, id + "@ex.com",
                Timestamp.from(threshold.minus(365, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now())
        );

        List<AccountJpaEntity> candidates = accountRepo.findActiveDormantCandidates(threshold);

        assertThat(candidates).extracting(AccountJpaEntity::getId).contains(id);
    }

    // ── findAnonymizationCandidates ─────────────────────────────────────────

    @Test
    @DisplayName("findAnonymizationCandidates — DELETED + grace 경과 + maskedAt=null → 반환")
    void findAnonymizationCandidates_deletedPastGrace_unmaskedProfile_returnsEligible() {
        Instant graceCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        String id = UUID.randomUUID().toString();

        // DELETED 40일 전
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, deleted_at, version) " +
                "VALUES (?, ?, ?, 'DELETED', ?, ?, ?, 0)",
                id, TENANT_FAN, id + "@ex.com",
                Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)),
                Timestamp.from(graceCutoff.minus(1, ChronoUnit.DAYS)),
                Timestamp.from(graceCutoff.minus(10, ChronoUnit.DAYS))
        );
        // maskedAt=null 프로파일
        jdbc.update(
                "INSERT INTO profiles (account_id, tenant_id, locale, timezone, updated_at) VALUES (?, ?, 'ko-KR', 'Asia/Seoul', ?)",
                id, TENANT_FAN, Timestamp.from(Instant.now())
        );

        List<AccountJpaEntity> candidates = accountRepo.findAnonymizationCandidates(graceCutoff);

        assertThat(candidates).extracting(AccountJpaEntity::getId).contains(id);
    }

    @Test
    @DisplayName("findAnonymizationCandidates — grace 기간 미경과 → 제외")
    void findAnonymizationCandidates_recentlyDeleted_excludes() {
        Instant graceCutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        String id = UUID.randomUUID().toString();

        // DELETED 10일 전 (grace 기간 이내)
        jdbc.update(
                "INSERT INTO accounts (id, tenant_id, email, status, created_at, updated_at, deleted_at, version) " +
                "VALUES (?, ?, ?, 'DELETED', ?, ?, ?, 0)",
                id, TENANT_FAN, id + "@ex.com",
                Timestamp.from(Instant.now().minus(15, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().minus(10, ChronoUnit.DAYS))
        );

        List<AccountJpaEntity> candidates = accountRepo.findAnonymizationCandidates(graceCutoff);

        assertThat(candidates).extracting(AccountJpaEntity::getId).doesNotContain(id);
    }

    // ── TASK-BE-386 (ADR-MONO-036 M4): production backfill ───────────────────────

    private void insertIdentity(String identityId, String tenantId, String email) {
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)", identityId, tenantId, email);
    }

    private void insertLinkedAccount(String accountId, String identityId, String tenantId, String email) {
        jdbc.update("INSERT INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(6), NOW(6), 0)", accountId, identityId, tenantId, email);
    }

    /** Replicates the V0024 backfill SQL (Flyway runs it once at startup with no orphans). */
    private void runV0024Backfill() {
        jdbc.update("INSERT INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version) "
                + "SELECT UUID(), a.tenant_id, a.email, 'ACTIVE', a.created_at, a.updated_at, 0 FROM accounts a "
                + "WHERE a.identity_id IS NULL AND NOT EXISTS "
                + "(SELECT 1 FROM identities i WHERE i.tenant_id = a.tenant_id AND i.primary_email = a.email)");
        jdbc.update("UPDATE accounts a JOIN identities i ON i.tenant_id = a.tenant_id AND i.primary_email = a.email "
                + "SET a.identity_id = i.identity_id WHERE a.identity_id IS NULL");
    }

    @Test
    @DisplayName("BE-386: findAllIdentityBindings — linked 계정만 (account_id→identity_id), cross-tenant 포함, unlinked 제외")
    void findAllIdentityBindings_returnsLinkedOnly() {
        insertWmsTenant();
        String idyFan = UUID.randomUUID().toString();
        String accFan = UUID.randomUUID().toString();
        insertIdentity(idyFan, TENANT_FAN, "lf@example.com");
        insertLinkedAccount(accFan, idyFan, TENANT_FAN, "lf@example.com");

        String idyWms = UUID.randomUUID().toString();
        String accWms = UUID.randomUUID().toString();
        insertIdentity(idyWms, TENANT_WMS, "lw@example.com");
        insertLinkedAccount(accWms, idyWms, TENANT_WMS, "lw@example.com");

        String accNull = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, accNull, "nolink@example.com"); // identity_id NULL

        List<AccountIdentityBindingView> bindings = accountRepo.findAllIdentityBindings();

        assertThat(bindings).extracting(AccountIdentityBindingView::getAccountId)
                .contains(accFan, accWms)        // cross-tenant, both linked accounts
                .doesNotContain(accNull);        // unlinked excluded
        assertThat(bindings)
                .anyMatch(b -> accFan.equals(b.getAccountId()) && idyFan.equals(b.getIdentityId()))
                .anyMatch(b -> accWms.equals(b.getAccountId()) && idyWms.equals(b.getIdentityId()));
    }

    @Test
    @DisplayName("BE-386: V0024 backfill SQL — orphan 에 identity mint+link, 기존 재사용, 무덮어쓰기, 멱등")
    void v0024Backfill_mintsLinksReusesIdempotent() {
        // orphan A: no pre-existing identity for its (tenant, email) → fresh mint
        String accA = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, accA, "orphanA@example.com");
        // orphan B: a same-origin identity already exists → must be REUSED, not duplicated
        String existingIdyB = UUID.randomUUID().toString();
        insertIdentity(existingIdyB, TENANT_FAN, "orphanB@example.com");
        String accB = UUID.randomUUID().toString();
        insertAccount(TENANT_FAN, accB, "orphanB@example.com");
        // already-linked C: must NOT be overwritten
        String idyC = UUID.randomUUID().toString();
        String accC = UUID.randomUUID().toString();
        insertIdentity(idyC, TENANT_FAN, "linkedC@example.com");
        insertLinkedAccount(accC, idyC, TENANT_FAN, "linkedC@example.com");

        runV0024Backfill();

        // A minted + linked (non-null, not equal to any pre-existing one)
        String aIdy = jdbc.queryForObject("SELECT identity_id FROM accounts WHERE id = ?", String.class, accA);
        assertThat(aIdy).isNotNull();
        // B reused the existing identity (no duplicate row)
        assertThat(jdbc.queryForObject("SELECT identity_id FROM accounts WHERE id = ?", String.class, accB))
                .isEqualTo(existingIdyB);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM identities WHERE tenant_id = ? AND primary_email = ?",
                Integer.class, TENANT_FAN, "orphanB@example.com")).isEqualTo(1);
        // C untouched (no overwrite)
        assertThat(jdbc.queryForObject("SELECT identity_id FROM accounts WHERE id = ?", String.class, accC))
                .isEqualTo(idyC);
        // no orphan remains
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE identity_id IS NULL", Integer.class))
                .isZero();

        // idempotent: a second run changes nothing (no new identities, no NULLs)
        Integer identitiesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM identities", Integer.class);
        runV0024Backfill();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM identities", Integer.class)).isEqualTo(identitiesBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE identity_id IS NULL", Integer.class))
                .isZero();
    }
}
