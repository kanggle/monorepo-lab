package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.page.PageQuery;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.MetricsPeriod;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderMetricsService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.ReceiveAsnCommand;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-810 (ADR-MONO-084 P5) — {@code GET /po/metrics} aggregation and the {@code GET /po}
 * submit-date filter against real PostgreSQL.
 *
 * <p>Every test runs in a <b>tenant of its own</b> ({@code m810-<random>}), so absolute values
 * can be asserted in a database shared with the other ITs, and tenant isolation is the
 * precondition of every assertion, not only of {@link #tenantIsolation()}. The application
 * services are called directly (the base class's convention — the HTTP / role / tenant gate is
 * covered by {@code ProcurementBusinessRoleGateSliceTest} and
 * {@code PurchaseOrderMetricsControllerSliceTest}).
 *
 * <p>POs are created through the domain factory (so {@code total_amount} is the aggregate's own
 * line total) and then moved to the wanted status / {@code submitted_at} with one SQL UPDATE —
 * the submit path needs the supplier mock and stamps "now", while these tests need exact
 * instants. One receipt goes through the real {@code receiveAsn} use case; the boundary rows of
 * {@code po_status_history} are inserted with explicit instants.
 */
@Tag("integration")
@DisplayName("TASK-MONO-810: PO performance metrics + submit-date filter (PostgreSQL)")
class PurchaseOrderMetricsIntegrationTest extends AbstractProcurementIntegrationTest {

    @Autowired
    private PurchaseOrderMetricsService metricsService;

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private JdbcTemplate jdbc;

    private String tenant;
    private ActorContext actor;

    @BeforeEach
    void setUp() {
        tenant = "m810-" + UUID.randomUUID().toString().substring(0, 8);
        actor = new ActorContext("acct-m810", tenant, Set.of("SCM_PROCUREMENT_READ"));
    }

    // ---- helpers -----------------------------------------------------------

    /** A PO with one line (qty 1 × {@code amount}) in {@code status}, submitted at {@code submittedAt} (null = never). */
    private String seedPo(String tenantId, String currency, String amount, PoStatus status, Instant submittedAt) {
        return seedPo(tenantId, currency, amount, "1", status, submittedAt);
    }

    private String seedPo(String tenantId, String currency, String amount, String quantity,
                          PoStatus status, Instant submittedAt) {
        String poId = UuidV7.randomString();
        String poNumber = "PO-M810-" + poId.replace("-", "").substring(20).toUpperCase();
        PurchaseOrder po = PurchaseOrder.createDraft(poId, tenantId, poNumber, "sup-m810", "buyer-m810", currency);
        BigDecimal qty = new BigDecimal(quantity);
        PurchaseOrderLine line = PurchaseOrderLine.create(UuidV7.randomString(), poId, tenantId,
                1, "sku-m810", null, qty, new BigDecimal(amount).divide(qty));
        po.addLine(line);
        poJpa.save(po);
        lineJpa.save(line);
        if (submittedAt == null) {
            jdbc.update("UPDATE purchase_orders SET status = ?, submitted_at = NULL WHERE id = ?",
                    status.name(), poId);
        } else {
            jdbc.update("UPDATE purchase_orders SET status = ?, submitted_at = ? WHERE id = ?",
                    status.name(), submittedAt.atOffset(ZoneOffset.UTC), poId);
        }
        return poId;
    }

    private void seedHistory(String tenantId, String poId, PoStatus from, PoStatus to, Instant occurredAt) {
        jdbc.update("INSERT INTO po_status_history (po_id, tenant_id, from_status, to_status, actor_type, occurred_at) "
                        + "VALUES (?, ?, ?, ?, 'SYSTEM', ?)",
                poId, tenantId, from.name(), to.name(), occurredAt.atOffset(ZoneOffset.UTC));
    }

    private PerformanceMetrics month() {
        return metricsService.getMetrics(actor, MetricsPeriod.MONTH);
    }

    private static PerformanceMetrics.Metric metric(PerformanceMetrics m, String key) {
        return m.metrics().stream().filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no metric " + key + " in " + m.metrics()));
    }

    private static String cur(PerformanceMetrics m, String key) {
        return metric(m, key).current();
    }

    private static String prev(PerformanceMetrics m, String key) {
        return metric(m, key).previous();
    }

    private List<String> listIds(PoStatus status, Instant from, Instant to) {
        return listIds(status, null, from, to);
    }

    private List<String> listIds(PoStatus status, PoStatus excludeStatus, Instant from, Instant to) {
        return page(status, excludeStatus, from, to).content().stream().map(PurchaseOrderView::id).toList();
    }

    private com.example.common.page.PageResult<PurchaseOrderView> page(PoStatus status, PoStatus excludeStatus,
                                                                        Instant from, Instant to) {
        return service.search(actor, status, excludeStatus, null, from, to,
                PageQuery.of(0, 50, "createdAt", "DESC"));
    }

    // ---- AC-1 control group ------------------------------------------------

    @Test
    @DisplayName("🔴 AC-1 control: DRAFT and submitted-then-CANCELED move neither poCount nor poAmount; one SUBMITTED PO does")
    void controlGroup() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        seedPo(tenant, "KRW", "1000", PoStatus.DRAFT, null);
        seedPo(tenant, "KRW", "2000", PoStatus.CANCELED, justNow);

        PerformanceMetrics before = month();
        assertThat(cur(before, "poCount")).isEqualTo("0");
        assertThat(cur(before, "poAmount:KRW")).isEqualTo("0");
        assertThat(before.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "receivedCount");

        seedPo(tenant, "KRW", "4000", PoStatus.SUBMITTED, justNow);

        PerformanceMetrics after = month();
        assertThat(cur(after, "poCount")).isEqualTo("1");
        assertThat(cur(after, "poAmount:KRW")).isEqualTo("4000");
        String today = KstPeriodBounds.dateOf(justNow).toString();
        assertThat(after.series()).filteredOn(p -> p.date().equals(today)).singleElement()
                .satisfies(p -> {
                    assertThat(p.values().get("poCount")).isEqualTo("1");
                    assertThat(p.values().get("poAmount:KRW")).isEqualTo("4000");
                });
    }

    @Test
    @DisplayName("every status after submit except CANCELED counts (SUBMITTED · ACKNOWLEDGED · CONFIRMED · PARTIALLY_RECEIVED · RECEIVED)")
    void everyNonCanceledSubmittedStatusCounts() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        for (PoStatus s : List.of(PoStatus.SUBMITTED, PoStatus.ACKNOWLEDGED, PoStatus.CONFIRMED,
                PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED)) {
            seedPo(tenant, "KRW", "100", s, justNow);
        }

        PerformanceMetrics m = month();

        assertThat(cur(m, "poCount")).isEqualTo("5");
        assertThat(cur(m, "poAmount:KRW")).isEqualTo("500");
    }

    @Test
    @DisplayName("multi-currency: KRW and USD each get their own key in minor units, never added together")
    void multiCurrency() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        seedPo(tenant, "KRW", "100000", PoStatus.SUBMITTED, justNow);
        seedPo(tenant, "USD", "10.00", PoStatus.CONFIRMED, justNow);
        seedPo(tenant, "USD", "2.34", PoStatus.SUBMITTED, justNow);

        PerformanceMetrics m = month();

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "poAmount:USD", "receivedCount");
        assertThat(cur(m, "poCount")).isEqualTo("3");
        assertThat(cur(m, "poAmount:KRW")).isEqualTo("100000");
        assertThat(cur(m, "poAmount:USD")).isEqualTo("1234");
        assertThat(metric(m, "poAmount:USD").currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("KRW amounts with a sub-won fraction are summed first and rounded once: 0.25 + 0.25 → 1")
    void roundOnceInDatabaseScale() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        seedPo(tenant, "KRW", "0.25", PoStatus.SUBMITTED, justNow);
        seedPo(tenant, "KRW", "0.25", PoStatus.SUBMITTED, justNow);

        assertThat(cur(month(), "poAmount:KRW")).isEqualTo("1");
    }

    // ---- windows -----------------------------------------------------------

    @Test
    @DisplayName("half-open windows: current.from inclusive, 1µs before excluded, future excluded; previous.from counts in previous")
    void halfOpenWindows() {
        KstPeriodBounds bounds = KstPeriodBounds.now();
        Instant monthStart = bounds.currentWindow(MetricsPeriod.MONTH).fromInstant();
        Instant prevStart = bounds.previousWindow(MetricsPeriod.MONTH).fromInstant();
        seedPo(tenant, "KRW", "1", PoStatus.SUBMITTED, monthStart);                                // in
        seedPo(tenant, "KRW", "10", PoStatus.SUBMITTED, Instant.now().minus(1, ChronoUnit.SECONDS)); // in
        seedPo(tenant, "KRW", "100", PoStatus.SUBMITTED, Instant.now().plus(1, ChronoUnit.HOURS));   // out (>= to)
        seedPo(tenant, "KRW", "1000", PoStatus.SUBMITTED, prevStart);                              // previous, in

        PerformanceMetrics m = month();

        assertThat(cur(m, "poCount")).isEqualTo("2");
        assertThat(cur(m, "poAmount:KRW")).isEqualTo("11");
        assertThat(prev(m, "poCount")).isEqualTo("1");
        assertThat(prev(m, "poAmount:KRW")).isEqualTo("1000");
    }

    // ---- receivedCount -----------------------------------------------------

    @Test
    @DisplayName("receivedCount: a real ASN full receipt counts; seeded RECEIVED transitions count by occurred_at, distinct per PO")
    void receivedCount() {
        // Real path: CONFIRMED PO (qty 10) fully received through the receiveAsn use case.
        String asnPo = seedPo(tenant, "KRW", "1000", "10", PoStatus.CONFIRMED,
                Instant.now().minus(40, ChronoUnit.DAYS));
        String lineId = lineJpa.findByPoIdOrderByLineNoAsc(asnPo).get(0).getId();
        service.receiveAsn(new ReceiveAsnCommand(tenant, asnPo, "ASN-M810-" + UUID.randomUUID(),
                Instant.now().plus(1, ChronoUnit.DAYS),
                List.of(new ReceiveAsnCommand.AsnLine(lineId, new BigDecimal("10")))));
        assertThat(poJpa.findByIdAndTenantId(asnPo, tenant).orElseThrow().getStatus())
                .isEqualTo(PoStatus.RECEIVED);

        Instant monthStart = KstPeriodBounds.now().currentWindow(MetricsPeriod.MONTH).fromInstant();
        String atBoundary = seedPo(tenant, "KRW", "1", PoStatus.RECEIVED, null);
        seedHistory(tenant, atBoundary, PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED, monthStart);          // in
        String justBefore = seedPo(tenant, "KRW", "1", PoStatus.RECEIVED, null);
        seedHistory(tenant, justBefore, PoStatus.CONFIRMED, PoStatus.RECEIVED,
                monthStart.minus(1, ChronoUnit.MICROS));                                                   // out
        String twice = seedPo(tenant, "KRW", "1", PoStatus.RECEIVED, null);
        Instant oneSecondAgo = Instant.now().minus(1, ChronoUnit.SECONDS);
        seedHistory(tenant, twice, PoStatus.CONFIRMED, PoStatus.RECEIVED, oneSecondAgo);                   // in
        seedHistory(tenant, twice, PoStatus.PARTIALLY_RECEIVED, PoStatus.RECEIVED, oneSecondAgo);          // same PO
        String partial = seedPo(tenant, "KRW", "1", PoStatus.PARTIALLY_RECEIVED, null);
        seedHistory(tenant, partial, PoStatus.CONFIRMED, PoStatus.PARTIALLY_RECEIVED, oneSecondAgo);       // not RECEIVED

        PerformanceMetrics m = month();

        // asnPo + atBoundary + twice (once) = 3
        assertThat(cur(m, "receivedCount")).isEqualTo("3");
        // receivedCount is independent of submit time: none of these was submitted this month
        assertThat(cur(m, "poCount")).isEqualTo("0");
    }

    @Test
    @DisplayName("🔴 TASK-SCM-BE-063: a full ASN on a CANCELED PO is rejected and does NOT move receivedCount; the same ASN on a CONFIRMED PO does")
    void rejectedReceiptDoesNotMoveReceivedCount() {
        String canceled = seedPo(tenant, "KRW", "1000", "10", PoStatus.CANCELED, null);
        String canceledLine = lineJpa.findByPoIdOrderByLineNoAsc(canceled).get(0).getId();

        assertThat(cur(month(), "receivedCount")).isEqualTo("0");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.receiveAsn(new ReceiveAsnCommand(
                        tenant, canceled, "ASN-063-" + UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.DAYS),
                        List.of(new ReceiveAsnCommand.AsnLine(canceledLine, new BigDecimal("10"))))))
                .isInstanceOf(com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException.class);
        assertThat(poJpa.findByIdAndTenantId(canceled, tenant).orElseThrow().getStatus())
                .isEqualTo(PoStatus.CANCELED);
        assertThat(cur(month(), "receivedCount")).isEqualTo("0");

        // Control: the same receipt on a CONFIRMED PO counts — the zero above is the guard, not a dead metric.
        String confirmed = seedPo(tenant, "KRW", "1000", "10", PoStatus.CONFIRMED, null);
        String confirmedLine = lineJpa.findByPoIdOrderByLineNoAsc(confirmed).get(0).getId();
        service.receiveAsn(new ReceiveAsnCommand(tenant, confirmed, "ASN-063-" + UUID.randomUUID(),
                Instant.now().plus(1, ChronoUnit.DAYS),
                List.of(new ReceiveAsnCommand.AsnLine(confirmedLine, new BigDecimal("10")))));
        assertThat(cur(month(), "receivedCount")).isEqualTo("1");
    }

    // ---- GET /po submit-date filter ------------------------------------------

    @Test
    @DisplayName("GET /po submittedFrom inclusive · submittedTo exclusive · DRAFT excluded when bounded · combines with status")
    void submitDateFilter() {
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-08T00:00:00Z");
        String atFrom = seedPo(tenant, "KRW", "1", PoStatus.SUBMITTED, from);
        String atTo = seedPo(tenant, "KRW", "1", PoStatus.SUBMITTED, to);
        String canceledInside = seedPo(tenant, "KRW", "1", PoStatus.CANCELED, from.plus(1, ChronoUnit.HOURS));
        String justBeforeFrom = seedPo(tenant, "KRW", "1", PoStatus.SUBMITTED, from.minus(1, ChronoUnit.MICROS));
        String justBeforeTo = seedPo(tenant, "KRW", "1", PoStatus.CONFIRMED, to.minus(1, ChronoUnit.MICROS));
        String draft = seedPo(tenant, "KRW", "1", PoStatus.DRAFT, null);

        assertThat(listIds(null, from, to))
                .containsExactlyInAnyOrder(atFrom, canceledInside, justBeforeTo);
        assertThat(listIds(PoStatus.SUBMITTED, from, to)).containsExactly(atFrom);
        assertThat(listIds(null, from, null))
                .containsExactlyInAnyOrder(atFrom, atTo, canceledInside, justBeforeTo);
        assertThat(listIds(null, null, to))
                .containsExactlyInAnyOrder(atFrom, canceledInside, justBeforeFrom, justBeforeTo);
        // Unbounded (both instants bound as typed NULLs) still works on PostgreSQL and keeps the DRAFT.
        assertThat(listIds(null, null, null))
                .containsExactlyInAnyOrder(atFrom, atTo, canceledInside, justBeforeFrom, justBeforeTo, draft);
        assertThat(listIds(PoStatus.DRAFT, null, null)).containsExactly(draft);
        // excludeStatus ANDs with the bounds: the canceled one inside the range drops out
        assertThat(listIds(null, PoStatus.CANCELED, from, to))
                .containsExactlyInAnyOrder(atFrom, justBeforeTo);
        // ... and with status (status=SUBMITTED, excludeStatus=CANCELED → the SUBMITTED one only)
        assertThat(listIds(PoStatus.SUBMITTED, PoStatus.CANCELED, from, to)).containsExactly(atFrom);
        // excludeStatus alone (no bounds) also drops it, and keeps the DRAFT
        assertThat(listIds(null, PoStatus.CANCELED, null, null))
                .containsExactlyInAnyOrder(atFrom, atTo, justBeforeFrom, justBeforeTo, draft);
        // from >= to → empty, not an error
        assertThat(listIds(null, to, from)).isEmpty();
    }

    @Test
    @DisplayName("🔴 card link (submittedFrom · submittedTo · excludeStatus=CANCELED) lists exactly poCount POs; without excludeStatus the canceled-after-submit PO makes the counts differ")
    void cardLinkTotalEqualsPoCount() {
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        String submitted = seedPo(tenant, "KRW", "10", PoStatus.SUBMITTED, justNow);
        String confirmed = seedPo(tenant, "KRW", "15", PoStatus.CONFIRMED, justNow);
        // submitted inside the window, then canceled: out of poCount, in the list unless excluded
        String canceledAfterSubmit = seedPo(tenant, "KRW", "20", PoStatus.CANCELED, justNow);
        seedPo(tenant, "KRW", "30", PoStatus.DRAFT, null);

        KstPeriodBounds bounds = KstPeriodBounds.now();
        Instant from = bounds.currentWindow(MetricsPeriod.MONTH).fromInstant();
        Instant to = Instant.now();

        long poCount = Long.parseLong(cur(month(), "poCount"));
        assertThat(poCount).isEqualTo(2);

        // The card link: totalElements == poCount, and the ids are the counted ones.
        assertThat(page(null, PoStatus.CANCELED, from, to).totalElements()).isEqualTo(poCount);
        assertThat(listIds(null, PoStatus.CANCELED, from, to)).containsExactlyInAnyOrder(submitted, confirmed);

        // Control: the same window WITHOUT excludeStatus disagrees with poCount — the case bites.
        assertThat(page(null, null, from, to).totalElements()).isEqualTo(3).isNotEqualTo(poCount);
        assertThat(listIds(null, from, to)).containsExactlyInAnyOrder(submitted, confirmed, canceledAfterSubmit);
    }

    // ---- tenant isolation ------------------------------------------------------

    @Test
    @DisplayName("tenant isolation: another tenant's submitted / received POs are neither counted nor listed")
    void tenantIsolation() {
        String other = "m810-other-" + UUID.randomUUID().toString().substring(0, 8);
        Instant justNow = Instant.now().minus(1, ChronoUnit.SECONDS);
        String foreignPo = seedPo(other, "KRW", "5000", PoStatus.SUBMITTED, justNow);
        String foreignReceived = seedPo(other, "USD", "1.00", PoStatus.RECEIVED, justNow);
        seedHistory(other, foreignReceived, PoStatus.CONFIRMED, PoStatus.RECEIVED, justNow);

        PerformanceMetrics mine = month();
        assertThat(cur(mine, "poCount")).isEqualTo("0");
        assertThat(cur(mine, "receivedCount")).isEqualTo("0");
        assertThat(mine.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "receivedCount");
        assertThat(cur(mine, "poAmount:KRW")).isEqualTo("0");
        assertThat(listIds(null, justNow.minus(1, ChronoUnit.HOURS), Instant.now())).isEmpty();

        // The other tenant does see its own rows — the zero above is isolation, not a broken query.
        PerformanceMetrics theirs = metricsService.getMetrics(
                new ActorContext("acct-other", other, Set.of("SCM_PROCUREMENT_READ")), MetricsPeriod.MONTH);
        assertThat(cur(theirs, "poCount")).isEqualTo("2");
        assertThat(cur(theirs, "poAmount:KRW")).isEqualTo("5000");
        assertThat(cur(theirs, "poAmount:USD")).isEqualTo("100");
        assertThat(cur(theirs, "receivedCount")).isEqualTo("1");
        assertThat(foreignPo).isNotNull();
    }
}
