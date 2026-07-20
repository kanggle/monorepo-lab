package com.example.settlement;

import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.PeriodAlreadyOpenException;
import com.example.settlement.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-535 ② — duplicate {@code POST /api/admin/settlements/periods} must not open a
 * second period over the same window (a double-payout vector: {@code close} folds the
 * in-window accruals into {@code seller_payout} rows, so two OPEN periods over one
 * accrual window each pay every seller).
 *
 * <p>This is the <b>authoritative</b> lane for the guard: the mechanism is the Flyway V6
 * <i>partial</i> unique index {@code (tenant_id, period_from, period_to) WHERE status =
 * 'OPEN'}, which only exists in a real Postgres. The unit test asserts the
 * {@code DataIntegrityViolationException → 409} translation; only this test proves the
 * constraint is actually there and actually partial.
 *
 * <p>★ Pinned to {@code SettlementServiceApplication.class}; {@code @Tag("integration")}
 * so it is excluded from the Docker-free {@code :test}.
 */
@SpringBootTest(classes = SettlementServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
class SettlementPeriodDuplicateOpenIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_db")
            .withUsername("settlement_user")
            .withPassword("settlement_pass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("settlement.commission.default-rate-bps", () -> "1000");
    }

    @Autowired
    private OpenSettlementPeriodUseCase openPeriod;
    @Autowired
    private CloseSettlementPeriodUseCase closePeriod;
    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("DELETE FROM settlement_outbox");
        jdbc.update("DELETE FROM seller_payout");
        jdbc.update("DELETE FROM settlement_period");
        jdbc.update("DELETE FROM commission_accrual");
    }

    private long openPeriodCount(String tenant) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM settlement_period "
                        + "WHERE tenant_id = ? AND status = 'OPEN'", Long.class, tenant);
    }

    /**
     * AC-4 — the replay is refused and, crucially, <b>no second OPEN period row exists</b>.
     * The row count is the assertion; a 409 alone would not prove the write was prevented.
     */
    @Test
    void duplicate_open_same_window_does_not_create_second_open_period() {
        TenantContext.set("tenantA");
        openPeriod.open("tenantA", FROM, TO);

        assertThatThrownBy(() -> openPeriod.open("tenantA", FROM, TO))
                .isInstanceOf(PeriodAlreadyOpenException.class);

        assertThat(openPeriodCount("tenantA")).isEqualTo(1);
    }

    /**
     * F1-equivalent regression guard for the settlement half: the index is scoped to
     * {@code status = 'OPEN'}, so re-opening the SAME window after the earlier period was
     * CLOSED (a correction re-run — e.g. recompute a month closed with a bad commission
     * rate) must still succeed. A full unique index would break this.
     */
    @Test
    void reopening_same_window_after_close_is_allowed() {
        TenantContext.set("tenantA");
        PeriodView first = openPeriod.open("tenantA", FROM, TO);
        closePeriod.close(first.periodId(), "tenantA", "operator-1");

        assertThatCode(() -> openPeriod.open("tenantA", FROM, TO)).doesNotThrowAnyException();

        assertThat(openPeriodCount("tenantA")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM settlement_period WHERE tenant_id = 'tenantA'", Long.class))
                .isEqualTo(2);
    }

    /**
     * The guard is exact-duplicate only, as decided. A genuinely OVERLAPPING (but not
     * identical) window remains permitted — {@code SettlementPeriod} documents overlapping
     * windows as intended behaviour, and narrowing that is a separate product decision.
     */
    @Test
    void overlapping_but_not_identical_window_is_still_allowed() {
        TenantContext.set("tenantA");
        openPeriod.open("tenantA", FROM, TO);

        assertThatCode(() -> openPeriod.open(
                "tenantA", Instant.parse("2026-06-15T00:00:00Z"), TO))
                .doesNotThrowAnyException();

        assertThat(openPeriodCount("tenantA")).isEqualTo(2);
    }

    /** The index is tenant-scoped: the same window in another tenant is not a duplicate (M6). */
    @Test
    void same_window_in_another_tenant_is_not_a_duplicate() {
        TenantContext.set("tenantA");
        openPeriod.open("tenantA", FROM, TO);

        assertThatCode(() -> openPeriod.open("tenantB", FROM, TO)).doesNotThrowAnyException();

        assertThat(openPeriodCount("tenantA")).isEqualTo(1);
        assertThat(openPeriodCount("tenantB")).isEqualTo(1);
    }
}
