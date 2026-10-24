package com.example.security.service.integration;

import com.example.security.service.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.service.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.example.security.service.query.SecurityQueryService;
import com.example.security.service.query.dto.LoginHistoryView;
import com.example.security.service.query.dto.SuspiciousEventView;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-656 — {@link SecurityQueryService}'s two account reads against a real MySQL with every Flyway migration
 * (V0013 {@code site_tenant_id} included). Before the ticket both reads were hard-coded to {@code fan-platform},
 * so any other tenant's account read empty (AC-2); the fix must not swing to «no tenant filter» either (AC-3).
 * Owner decision 2026-10-25 «그 사이트 것만»: a consumer-pool account's login to site X shows in X's view (the
 * TASK-BE-653 predicate {@code tenant_id = X OR site_tenant_id = X}); its suspicious events, which carry
 * {@code consumer-pool} and no site, do not. Random tenant and account ids per test; {@code @DataJpaTest} rolls
 * back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
// Tagged so the CI iam-integration-tests lane (`:integrationTest`, Docker) runs it as a reported result —
// an untagged Testcontainers class in `test` only skips quietly where Docker is absent.
@Tag("integration")
@Import(SecurityQueryService.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@DisplayName("SecurityQueryService — tenant of the login-history / suspicious-event reads (TASK-BE-656)")
class SecurityQueryTenantIntegrationTest {

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

    private static final String POOL = "consumer-pool";
    private static final String FAN = "fan-platform";
    private static final String ALL = "*";
    private static final Instant FROM = Instant.parse("2021-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2021-12-31T00:00:00Z");
    private static final PageRequest PAGE = PageRequest.of(0, 50);

    @Autowired LoginHistoryJpaRepository loginRepo;
    @Autowired SuspiciousEventJpaRepository suspiciousRepo;
    @Autowired SecurityQueryService service;

    private static String tenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** A login row of {@code accountId} recorded under {@code tenantId}, with {@code siteTenantId} (nullable). */
    private String login(String tenantId, String siteTenantId, String accountId, String at) {
        String eventId = uuid();
        loginRepo.saveAndFlush(LoginHistoryJpaEntity.from(tenantId, siteTenantId, eventId, accountId, "SUCCESS",
                "1.2.*.*", "Chrome", "fp", "KR", Instant.parse(at)));
        return eventId;
    }

    private String suspicious(String tenantId, String accountId, String ruleCode, String at) {
        String id = uuid();
        suspiciousRepo.saveAndFlush(SuspiciousEventJpaEntity.create(id, tenantId, accountId, ruleCode, 80, "ALERT",
                "{}", uuid(), Instant.parse(at), null));
        return id;
    }

    private List<String> logins(String tenantId, String accountId) {
        return service.findLoginHistory(tenantId, accountId, FROM, TO, null, PAGE).getContent().stream()
                .map(LoginHistoryView::eventId).toList();
    }

    private List<String> events(String tenantId, String accountId, String ruleCode) {
        return service.findSuspiciousEvents(tenantId, accountId, FROM, TO, ruleCode, PAGE).getContent().stream()
                .map(SuspiciousEventView::id).toList();
    }

    // ── AC-2 — an account outside fan-platform is no longer empty ────────────────────────────────

    @Test
    @DisplayName("🔴 AC-2: a non-fan-platform account's login history is returned for its own tenant")
    void loginHistory_nonFanPlatformAccount_returned() {
        String t = tenant();
        String acc = uuid();
        String e1 = login(t, null, acc, "2021-03-01T01:00:00Z");
        String e2 = login(t, null, acc, "2021-03-01T02:00:00Z");

        assertThat(logins(t, acc)).containsExactly(e2, e1);   // newest first
    }

    @Test
    @DisplayName("🔴 AC-2: a non-fan-platform account's suspicious events are returned for its own tenant (with and without ruleCode)")
    void suspicious_nonFanPlatformAccount_returned() {
        String t = tenant();
        String acc = uuid();
        String geo = suspicious(t, acc, "GEO_ANOMALY", "2021-03-01T01:00:00Z");
        String vel = suspicious(t, acc, "VELOCITY", "2021-03-01T02:00:00Z");

        assertThat(events(t, acc, null)).containsExactly(vel, geo);
        assertThat(events(t, acc, "GEO_ANOMALY")).containsExactly(geo);
    }

    // ── AC-3 — isolation: tenant A never sees tenant B's rows; fan-platform unchanged ─────────────

    @Test
    @DisplayName("🔴 AC-3 isolation: a query scoped to tenant A returns none of tenant B's login rows for the same account id")
    void loginHistory_isolation() {
        String a = tenant();
        String b = tenant();
        String acc = uuid();
        String inA = login(a, null, acc, "2021-03-01T01:00:00Z");
        String inB = login(b, null, acc, "2021-03-01T02:00:00Z");
        login(a, null, uuid(), "2021-03-01T03:00:00Z");   // another account in A — never matches acc

        assertThat(logins(a, acc)).containsExactly(inA);
        assertThat(logins(b, acc)).containsExactly(inB);
        assertThat(logins(tenant(), acc)).isEmpty();   // a third tenant sees nothing of either
    }

    @Test
    @DisplayName("🔴 AC-3 isolation: a query scoped to tenant A returns none of tenant B's suspicious events (with and without ruleCode)")
    void suspicious_isolation() {
        String a = tenant();
        String b = tenant();
        String acc = uuid();
        String inA = suspicious(a, acc, "GEO_ANOMALY", "2021-03-01T01:00:00Z");
        suspicious(b, acc, "GEO_ANOMALY", "2021-03-01T02:00:00Z");

        assertThat(events(a, acc, null)).containsExactly(inA);
        assertThat(events(a, acc, "GEO_ANOMALY")).containsExactly(inA);
        assertThat(events(tenant(), acc, null)).isEmpty();
    }

    @Test
    @DisplayName("AC-3 control: a fan-platform account's result is unchanged — its own rows, and not another tenant's")
    void fanPlatform_control() {
        String acc = uuid();
        String login = login(FAN, null, acc, "2021-03-01T01:00:00Z");
        String event = suspicious(FAN, acc, "DEVICE_CHANGE", "2021-03-01T01:00:00Z");
        login(tenant(), null, acc, "2021-03-01T02:00:00Z");
        suspicious(tenant(), acc, "DEVICE_CHANGE", "2021-03-01T02:00:00Z");

        assertThat(logins(FAN, acc)).containsExactly(login);
        assertThat(events(FAN, acc, null)).containsExactly(event);
    }

    // ── consumer pool — owner decision «그 사이트 것만» ─────────────────────────────────────────

    @Test
    @DisplayName("🔴 pool account: site X sees only its logins to X (not to Y, not site-less); none of its suspicious events")
    void poolAccount_siteSeesOnlyItsOwnLogins() {
        String x = tenant();
        String y = tenant();
        String p = uuid();
        String toX = login(POOL, x, p, "2021-03-01T01:00:00Z");
        String toY = login(POOL, y, p, "2021-03-01T02:00:00Z");
        String noSite = login(POOL, null, p, "2021-03-01T03:00:00Z");   // pre-TASK-BE-653 row, or no initiating site
        String event = suspicious(POOL, p, "GEO_ANOMALY", "2021-03-01T04:00:00Z");

        assertThat(logins(x, p)).containsExactly(toX);
        assertThat(logins(y, p)).containsExactly(toY);
        assertThat(events(x, p, null)).isEmpty();
        assertThat(events(x, p, "GEO_ANOMALY")).isEmpty();

        // the pool's own view: every pool login and the account-level event
        assertThat(logins(POOL, p)).containsExactly(noSite, toY, toX);
        assertThat(events(POOL, p, null)).containsExactly(event);
    }

    @Test
    @DisplayName("'*' (platform scope): no tenant filter — every row of the account, pool and site rows each once")
    void allTenants_noFilter() {
        String a = tenant();
        String x = tenant();
        String acc = uuid();
        String inA = login(a, null, acc, "2021-03-01T01:00:00Z");
        String poolToX = login(POOL, x, acc, "2021-03-01T02:00:00Z");
        login(a, null, uuid(), "2021-03-01T03:00:00Z");   // another account — never matches
        String evA = suspicious(a, acc, "VELOCITY", "2021-03-01T01:00:00Z");
        String evPool = suspicious(POOL, acc, "VELOCITY", "2021-03-01T02:00:00Z");

        assertThat(logins(ALL, acc)).containsExactly(poolToX, inA);
        assertThat(events(ALL, acc, null)).containsExactly(evPool, evA);
        assertThat(events(ALL, acc, "VELOCITY")).containsExactly(evPool, evA);
        assertThat(events(ALL, acc, "GEO_ANOMALY")).isEmpty();
    }

    @Test
    @DisplayName("🔴 a blank tenant is refused — never widened to every tenant")
    void blankTenant_refused() {
        String acc = uuid();
        login(tenant(), null, acc, "2021-03-01T01:00:00Z");

        assertThatThrownBy(() -> service.findLoginHistory(" ", acc, FROM, TO, null, PAGE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findSuspiciousEvents(null, acc, FROM, TO, null, PAGE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
