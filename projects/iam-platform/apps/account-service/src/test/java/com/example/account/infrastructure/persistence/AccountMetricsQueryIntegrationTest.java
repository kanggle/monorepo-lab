package com.example.account.infrastructure.persistence;

import com.example.account.application.result.AccountMetricsDailyResult;
import com.example.account.application.service.AccountMetricsQueryService;
import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-811 AC-1 — {@code newAccount} against a real MySQL with every Flyway migration (V0034
 * indexes included): the tenant filter, the pool-membership rule for a consumer site, no double count
 * under {@code '*'}, the half-open window and the re-join rule ({@code consented_at} is overwritten).
 * Every test uses fresh random tenants and a window years in the past; {@code @DataJpaTest} rolls back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
// Tagged so the CI iam-integration-tests lane (`:integrationTest`, Docker) runs it as a reported result —
// an untagged Testcontainers class in `test` only skips quietly where Docker is absent.
@Tag("integration")
@Import({AccountMetricsQueryService.class, AccountMetricsQueryPortImpl.class})
@DisplayName("AccountMetricsQueryService — MySQL (TASK-MONO-811)")
class AccountMetricsQueryIntegrationTest {

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

    // 2021-03-01 00:00 KST → 2021-03-05 00:00 KST
    private static final Instant FROM = Instant.parse("2021-02-28T15:00:00Z");
    private static final Instant TO = Instant.parse("2021-03-04T15:00:00Z");

    @Autowired AccountJpaRepository accountRepo;
    @Autowired ConsumerSiteMembershipJpaRepository membershipRepo;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountMetricsQueryService service;

    private String newTenant() {
        String id = "t-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at) "
                + "VALUES (?, 'metrics-it', 'B2C_CONSUMER', 'ACTIVE', NOW(6), NOW(6))", id);
        return id;
    }

    private String account(String tenantId, AccountStatus status, String createdAt) {
        String id = UUID.randomUUID().toString();
        Instant at = Instant.parse(createdAt);
        accountRepo.saveAndFlush(AccountJpaEntity.fromDomain(Account.reconstitute(
                id, new TenantId(tenantId), id.substring(0, 8) + "@metrics.test", null, status,
                at, at, status == AccountStatus.DELETED ? at : null, null, null, 0)));
        return id;
    }

    private void join(String accountId, String siteTenantId, String status, String consentedAt) {
        membershipRepo.insertMembership(accountId, siteTenantId, status, Instant.parse(consentedAt));
    }

    private static long on(AccountMetricsDailyResult r, String date) {
        return r.days().stream().filter(d -> d.date().equals(LocalDate.parse(date)))
                .mapToLong(AccountMetricsDailyResult.Day::newAccount).sum();
    }

    private static long total(AccountMetricsDailyResult r) {
        return r.days().stream().mapToLong(AccountMetricsDailyResult.Day::newAccount).sum();
    }

    @Test
    @DisplayName("🔴 tenant isolation: another tenant's accounts are not counted; every status of our own is")
    void tenantIsolation() {
        String site = newTenant();
        String other = newTenant();
        account(site, AccountStatus.ACTIVE, "2021-03-01T01:00:00Z");
        account(site, AccountStatus.LOCKED, "2021-03-01T02:00:00Z");
        account(site, AccountStatus.DELETED, "2021-03-02T02:00:00Z");
        account(other, AccountStatus.ACTIVE, "2021-03-01T01:00:00Z");
        account(other, AccountStatus.ACTIVE, "2021-03-01T01:30:00Z");

        AccountMetricsDailyResult r = service.daily(site, FROM, TO);

        assertThat(on(r, "2021-03-01")).isEqualTo(2);
        assertThat(on(r, "2021-03-02")).isEqualTo(1);
        assertThat(total(r)).isEqualTo(3);
    }

    @Test
    @DisplayName("🔴 a pool consumer joining the site IS a new account for that site; joining ANOTHER site is not")
    void poolMembershipCountsForTheSite() {
        String site = newTenant();
        String otherSite = newTenant();
        // the pool account itself was created long before the window — only the join is in it
        String pool = account("consumer-pool", AccountStatus.ACTIVE, "2020-06-01T00:00:00Z");
        String pool2 = account("consumer-pool", AccountStatus.ACTIVE, "2020-06-01T00:00:00Z");
        join(pool, site, "ACTIVE", "2021-03-03T05:00:00Z");
        join(pool2, otherSite, "ACTIVE", "2021-03-03T05:00:00Z");

        AccountMetricsDailyResult r = service.daily(site, FROM, TO);

        assertThat(on(r, "2021-03-03")).isEqualTo(1);
        assertThat(total(r)).isEqualTo(1);
    }

    @Test
    @DisplayName("a membership that is LEFT now still counts at its consented_at (the join happened)")
    void leftMembershipStillCounts() {
        String site = newTenant();
        String pool = account("consumer-pool", AccountStatus.ACTIVE, "2020-06-01T00:00:00Z");
        join(pool, site, "ACTIVE", "2021-03-02T05:00:00Z");
        membershipRepo.updateMembership(site, pool, "LEFT", Instant.parse("2021-03-02T05:00:00Z"),
                Instant.parse("2021-03-03T05:00:00Z"), "SELF", pool, null, null);

        assertThat(on(service.daily(site, FROM, TO), "2021-03-02")).isEqualTo(1);
    }

    @Test
    @DisplayName("re-join after leaving overwrites consented_at → counted once, on the re-join date; the old date drops")
    void rejoinMovesTheJoin() {
        String site = newTenant();
        String pool = account("consumer-pool", AccountStatus.ACTIVE, "2020-06-01T00:00:00Z");
        join(pool, site, "ACTIVE", "2021-03-01T05:00:00Z");
        // ConsentToConsumerSiteUseCase → rejoinOnConsent(now): ACTIVE again, consented_at = the new consent
        membershipRepo.updateMembership(site, pool, "ACTIVE", Instant.parse("2021-03-04T05:00:00Z"),
                null, null, null, null, null);

        AccountMetricsDailyResult r = service.daily(site, FROM, TO);

        assertThat(on(r, "2021-03-01")).isZero();
        assertThat(on(r, "2021-03-04")).isEqualTo(1);
    }

    @Test
    @DisplayName("consumer-pool: its own accounts rows only")
    void poolTenant() {
        String site = newTenant();
        String pool = account("consumer-pool", AccountStatus.ACTIVE, "2021-03-02T01:00:00Z");
        join(pool, site, "ACTIVE", "2021-03-02T01:00:00Z");

        AccountMetricsDailyResult r = service.daily("consumer-pool", FROM, TO);

        assertThat(on(r, "2021-03-02")).isEqualTo(1);
    }

    @Test
    @DisplayName("🔴 '*': a pool consumer who signed up AND joined a site in the window counts ONCE")
    void allTenantsNoDoubleCount() {
        String site = newTenant();
        account(site, AccountStatus.ACTIVE, "2021-03-02T01:00:00Z");
        String pool = account("consumer-pool", AccountStatus.ACTIVE, "2021-03-02T02:00:00Z");
        join(pool, site, "ACTIVE", "2021-03-02T02:00:00Z");

        AccountMetricsDailyResult r = service.daily("*", FROM, TO);

        assertThat(on(r, "2021-03-02")).isEqualTo(2);
    }

    @Test
    @DisplayName("half-open [from, to): created exactly at `from` counts, exactly at `to` does not; KST date cut")
    void halfOpenAndKstDate() {
        String site = newTenant();
        account(site, AccountStatus.ACTIVE, "2021-02-28T15:00:00Z");   // == from → 2021-03-01 KST
        account(site, AccountStatus.ACTIVE, "2021-02-28T14:59:59Z");   // before
        account(site, AccountStatus.ACTIVE, "2021-03-04T15:00:00Z");   // == to
        account(site, AccountStatus.ACTIVE, "2021-03-01T15:00:00Z");   // 2021-03-02 00:00 KST

        AccountMetricsDailyResult r = service.daily(site, FROM, TO);

        assertThat(on(r, "2021-03-01")).isEqualTo(1);
        assertThat(on(r, "2021-03-02")).isEqualTo(1);
        assertThat(total(r)).isEqualTo(2);
    }
}
