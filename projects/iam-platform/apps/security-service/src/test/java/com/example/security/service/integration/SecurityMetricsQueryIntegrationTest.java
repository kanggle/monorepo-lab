package com.example.security.service.integration;

import com.example.security.service.infrastructure.persistence.AccountLockHistoryJpaEntity;
import com.example.security.service.infrastructure.persistence.AccountLockHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.query.SecurityMetricsQueryService;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-811 AC-1 — {@link SecurityMetricsQueryService} against a real MySQL with every Flyway
 * migration (V0012 indexes included). Proves on the database what the unit test can only assume:
 * the tenant filter, the half-open window, and that ATTEMPTED / RATE_LIMITED / TOKEN_REUSE / REFRESH
 * rows are not counted. Each test uses its own random tenant ids and a window years in the past, and
 * {@code @DataJpaTest} rolls back, so the append-only tables need no cleanup.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
// Tagged so the CI iam-integration-tests lane (`:integrationTest`, Docker) runs it as a reported result —
// an untagged Testcontainers class in `test` only skips quietly where Docker is absent.
@Tag("integration")
@Import(SecurityMetricsQueryService.class)
@DisplayName("SecurityMetricsQueryService — MySQL (TASK-MONO-811)")
class SecurityMetricsQueryIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("security_db")
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

    // Mon 2021-03-01 00:00 KST → Wed 2021-03-03 12:00 KST
    private static final Instant FROM = Instant.parse("2021-02-28T15:00:00Z");
    private static final Instant TO = Instant.parse("2021-03-03T03:00:00Z");

    @Autowired LoginHistoryJpaRepository loginRepo;
    @Autowired AccountLockHistoryJpaRepository lockRepo;
    @Autowired SecurityMetricsQueryService service;

    private static String tenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void login(String tenantId, String outcome, String at) {
        loginRepo.saveAndFlush(LoginHistoryJpaEntity.from(tenantId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), outcome, "1.2.*.*", "Chrome", "fp", "KR", Instant.parse(at)));
    }

    private void lock(String tenantId, String at) {
        lockRepo.saveAndFlush(AccountLockHistoryJpaEntity.create(tenantId, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), "ADMIN_LOCK", "op-1", "admin", Instant.parse(at)));
    }

    private static SecurityMetricsDailyView.Day day(SecurityMetricsDailyView v, String date) {
        return v.days().stream().filter(d -> d.date().equals(date)).findFirst()
                .orElse(new SecurityMetricsDailyView.Day(date, 0, 0, 0));
    }

    @Test
    @DisplayName("🔴 control: only SUCCESS / FAILURE move the login keys; ATTEMPTED, RATE_LIMITED, TOKEN_REUSE, REFRESH move nothing")
    void controlGroup() {
        String t = tenant();
        login(t, "SUCCESS", "2021-03-01T01:00:00Z");
        login(t, "SUCCESS", "2021-03-01T02:00:00Z");
        login(t, "FAILURE", "2021-03-01T03:00:00Z");
        login(t, "ATTEMPTED", "2021-03-01T04:00:00Z");
        login(t, "RATE_LIMITED", "2021-03-01T05:00:00Z");
        login(t, "TOKEN_REUSE", "2021-03-01T06:00:00Z");
        login(t, "REFRESH", "2021-03-01T07:00:00Z");
        lock(t, "2021-03-01T08:00:00Z");
        lock(t, "2021-03-01T09:00:00Z");   // lock → unlock → relock = two rows

        SecurityMetricsDailyView v = service.daily(t, FROM, TO);

        assertThat(v.days()).containsExactly(new SecurityMetricsDailyView.Day("2021-03-01", 2, 1, 2));
    }

    @Test
    @DisplayName("🔴 one FAILURE row → only loginFailure moves")
    void oneFailureMovesOnlyFailure() {
        String t = tenant();
        login(t, "FAILURE", "2021-03-02T01:00:00Z");

        SecurityMetricsDailyView v = service.daily(t, FROM, TO);

        assertThat(v.days()).containsExactly(new SecurityMetricsDailyView.Day("2021-03-02", 0, 1, 0));
    }

    @Test
    @DisplayName("🔴 tenant isolation: another tenant's logins and locks — and a pool account's (consumer-pool) — are not counted")
    void tenantIsolationAndPoolExclusion() {
        String site = tenant();
        String other = tenant();
        login(site, "SUCCESS", "2021-03-01T01:00:00Z");
        login(other, "SUCCESS", "2021-03-01T01:00:00Z");
        login(other, "FAILURE", "2021-03-01T01:00:00Z");
        lock(other, "2021-03-01T01:00:00Z");
        // a pool consumer logging into the site is recorded under consumer-pool (the account's tenant)
        login("consumer-pool", "SUCCESS", "2021-03-01T01:30:00Z");
        lock("consumer-pool", "2021-03-01T01:30:00Z");

        SecurityMetricsDailyView v = service.daily(site, FROM, TO);

        assertThat(v.days()).containsExactly(new SecurityMetricsDailyView.Day("2021-03-01", 1, 0, 0));
    }

    @Test
    @DisplayName("half-open [from, to): a row at exactly `from` counts, one at exactly `to` does not; KST date cut")
    void halfOpenWindowAndKstDate() {
        String t = tenant();
        login(t, "SUCCESS", "2021-02-28T15:00:00Z");      // == from → 2021-03-01 00:00 KST, counted
        login(t, "SUCCESS", "2021-02-28T14:59:59.999999Z"); // before from, not counted
        login(t, "SUCCESS", "2021-03-03T03:00:00Z");      // == to, not counted
        lock(t, "2021-03-01T14:59:59Z");                   // 2021-03-01 23:59:59 KST
        lock(t, "2021-03-01T15:00:00Z");                   // 2021-03-02 00:00 KST

        SecurityMetricsDailyView v = service.daily(t, FROM, TO);

        assertThat(day(v, "2021-03-01")).isEqualTo(new SecurityMetricsDailyView.Day("2021-03-01", 1, 0, 1));
        assertThat(day(v, "2021-03-02")).isEqualTo(new SecurityMetricsDailyView.Day("2021-03-02", 0, 0, 1));
        assertThat(day(v, "2021-03-03").loginSuccess()).isZero();
    }

    @Test
    @DisplayName("'*' counts every tenant, consumer-pool included")
    void allTenants() {
        String a = tenant();
        String b = tenant();
        login(a, "SUCCESS", "2021-03-02T01:00:00Z");
        login(b, "SUCCESS", "2021-03-02T01:00:00Z");
        login("consumer-pool", "FAILURE", "2021-03-02T01:00:00Z");
        lock(b, "2021-03-02T01:00:00Z");

        SecurityMetricsDailyView v = service.daily("*", FROM, TO);

        assertThat(day(v, "2021-03-02")).isEqualTo(new SecurityMetricsDailyView.Day("2021-03-02", 2, 1, 1));
    }
}
