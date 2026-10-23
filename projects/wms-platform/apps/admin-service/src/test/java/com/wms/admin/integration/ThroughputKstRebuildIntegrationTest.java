package com.wms.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.PutawayFactRepository;
import com.wms.admin.readmodel.throughput.TimedQuantity;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-MONO-808 — the V6 KST day rebuild and the per-event metrics queries, on a real
 * Postgres.
 *
 * <p>🔴 AC-2 preservation control: the rebuild moves day boundaries only — over the whole
 * range the totals before and after are the same. Seeded the way the old projection wrote
 * them (UTC day), so the before-state is what an existing volume actually holds.
 *
 * <p>Each test clears the tables inside its own transaction and is rolled back, because
 * the container is shared with the other integration classes.
 */
@Tag("integration")
@SpringBootTest(classes = com.wms.admin.AdminServiceApplication.class,
        properties = {
                // must match every class sharing AdminServiceIntegrationBase's container
                // (see ReadModelPersistenceIntegrationTest)
                "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        })
@ActiveProfiles({"test", "standalone"})
@ContextConfiguration(initializers = AdminServiceIntegrationBase.Initializer.class)
@Transactional
class ThroughputKstRebuildIntegrationTest extends AdminServiceIntegrationBase {

    private static final UUID WH_A = UUID.fromString("00000000-0000-0000-0000-00000000a808");
    private static final UUID WH_B = UUID.fromString("00000000-0000-0000-0000-00000000b808");

    @Autowired JdbcTemplate jdbc;
    @Autowired PutawayFactRepository putawayFactRepo;
    @Autowired ShipmentSummaryRepository shipmentRepo;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM admin_throughput_outbound_daily");
        jdbc.update("DELETE FROM admin_throughput_inbound_daily");
        jdbc.update("DELETE FROM admin_putaway_fact");
        jdbc.update("DELETE FROM admin_shipment_summary");
    }

    // ---- outbound ----------------------------------------------------------------------

    @Test
    @DisplayName("출고: UTC 날짜로 쌓인 행을 KST 날짜로 다시 쌓는다 — 날만 옮겨지고 합계는 같다")
    void outbound_rebuild_movesDaysPreservesTotals() {
        // 05-09 15:30Z = 05-10 00:30 KST — the row that moves
        shipment(WH_A, "2026-05-09T15:30:00Z", 10);
        // 05-09 03:00Z = 05-09 12:00 KST — stays
        shipment(WH_A, "2026-05-09T03:00:00Z", 20);
        // 05-09 14:59:59Z = 05-09 23:59:59 KST — stays (boundary control)
        shipment(WH_B, "2026-05-09T14:59:59Z", 5);
        // what the old projection wrote: everything on UTC 05-09
        outboundDaily(LocalDate.of(2026, 5, 9), WH_A, 2, 30);
        outboundDaily(LocalDate.of(2026, 5, 9), WH_B, 1, 5);

        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM admin_rebuild_outbound_daily_kst()");

        assertThat(r).containsEntry("before_count", 3L).containsEntry("after_count", 3L)
                .containsEntry("before_qty", 35L).containsEntry("after_qty", 35L);
        assertThat(outboundRow(LocalDate.of(2026, 5, 9), WH_A)).containsExactly(1, 20);
        assertThat(outboundRow(LocalDate.of(2026, 5, 10), WH_A)).containsExactly(1, 10);
        assertThat(outboundRow(LocalDate.of(2026, 5, 9), WH_B)).containsExactly(1, 5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_throughput_outbound_daily", Long.class))
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("출고: 카운터와 요약이 어긋난 옛 볼륨은 요약을 따르고 차이를 돌려준다")
    void outbound_rebuild_followsSummaryWhenCounterDisagrees() {
        shipment(WH_A, "2026-05-09T03:00:00Z", 20);
        shipment(WH_A, "2026-05-09T04:00:00Z", 30);
        // pre-TASK-BE-643: the counter dropped one shipment the summary kept
        outboundDaily(LocalDate.of(2026, 5, 9), WH_A, 1, 20);

        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM admin_rebuild_outbound_daily_kst()");

        assertThat(r).containsEntry("before_count", 1L).containsEntry("after_count", 2L)
                .containsEntry("before_qty", 20L).containsEntry("after_qty", 50L);
        assertThat(outboundRow(LocalDate.of(2026, 5, 9), WH_A)).containsExactly(2, 50);
    }

    // ---- inbound -----------------------------------------------------------------------

    @Test
    @DisplayName("입고: 건별 행이 일별 행보다 적으면 거부하고 아무것도 안 바꾼다")
    void inbound_rebuild_refusesWhenFactsIncomplete() {
        // an existing volume right after V6: old UTC daily rows, no facts yet
        inboundDaily(LocalDate.of(2026, 5, 9), WH_A, 4, 400);
        fact(WH_A, "2026-05-20T01:00:00Z", 100); // one putaway since V6

        jdbc.execute("SAVEPOINT before_rebuild");
        assertThatThrownBy(() -> jdbc.queryForMap("SELECT * FROM admin_rebuild_inbound_daily_kst()"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("backfill from inbound_outbox first");
        jdbc.execute("ROLLBACK TO SAVEPOINT before_rebuild");

        assertThat(inboundRow(LocalDate.of(2026, 5, 9), WH_A)).containsExactly(4, 400);
    }

    @Test
    @DisplayName("입고: 건별 행이 다 있으면 KST 날짜로 다시 쌓고 합계는 같다")
    void inbound_rebuild_afterBackfill_movesDaysPreservesTotals() {
        fact(WH_A, "2026-05-09T15:30:00Z", 7);  // 05-10 00:30 KST
        fact(WH_A, "2026-05-09T02:00:00Z", 3);  // 05-09 11:00 KST
        fact(WH_B, "2026-05-31T16:00:00Z", 11); // 06-01 01:00 KST — crosses a month
        inboundDaily(LocalDate.of(2026, 5, 9), WH_A, 2, 10);
        inboundDaily(LocalDate.of(2026, 5, 31), WH_B, 1, 11);

        Map<String, Object> r = jdbc.queryForMap("SELECT * FROM admin_rebuild_inbound_daily_kst()");

        assertThat(r).containsEntry("before_count", 3L).containsEntry("after_count", 3L)
                .containsEntry("before_qty", 21L).containsEntry("after_qty", 21L);
        assertThat(inboundRow(LocalDate.of(2026, 5, 9), WH_A)).containsExactly(1, 3);
        assertThat(inboundRow(LocalDate.of(2026, 5, 10), WH_A)).containsExactly(1, 7);
        assertThat(inboundRow(LocalDate.of(2026, 6, 1), WH_B)).containsExactly(1, 11);
        assertThat(inboundRow(LocalDate.of(2026, 5, 31), WH_B)).isEmpty();
    }

    @Test
    @DisplayName("입고 건별 행: 같은 이벤트를 두 번 넣어도 한 행")
    void putawayFact_insertIfAbsent_isIdempotent() {
        UUID eventId = UUID.randomUUID();
        Instant at = Instant.parse("2026-05-09T01:00:00Z");
        assertThat(putawayFactRepo.insertIfAbsent(eventId, WH_A, at, 5)).isEqualTo(1);
        assertThat(putawayFactRepo.insertIfAbsent(eventId, WH_A, at, 5)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_putaway_fact", Long.class)).isEqualTo(1L);
    }

    // ---- metrics queries ---------------------------------------------------------------

    @Test
    @DisplayName("실적 쿼리: 구간은 [from, to) · 창고 필터 · 창고 없음은 전체")
    void metricsQueries_halfOpenWindow_warehouseFilter() {
        Instant from = Instant.parse("2026-05-10T15:00:00Z");
        Instant to = Instant.parse("2026-05-11T15:00:00Z");
        fact(WH_A, "2026-05-10T15:00:00Z", 1);  // at from → in
        fact(WH_A, "2026-05-11T15:00:00Z", 2);  // at to → out
        fact(WH_B, "2026-05-11T00:00:00Z", 4);  // other warehouse → in only unfiltered
        shipment(WH_A, "2026-05-10T15:00:00Z", 10);
        shipment(WH_A, "2026-05-11T15:00:00Z", 20);
        shipment(WH_B, "2026-05-11T00:00:00Z", 40);

        assertThat(putawayFactRepo.findInWindow(null, from, to)).extracting(TimedQuantity::qty)
                .containsExactlyInAnyOrder(1L, 4L);
        assertThat(putawayFactRepo.findInWindow(WH_A, from, to)).extracting(TimedQuantity::qty)
                .containsExactly(1L);
        assertThat(shipmentRepo.findShippedInWindow(null, from, to)).extracting(TimedQuantity::qty)
                .containsExactlyInAnyOrder(10L, 40L);
        assertThat(shipmentRepo.findShippedInWindow(WH_B, from, to)).extracting(TimedQuantity::qty)
                .containsExactly(40L);
    }

    // ---- seeding -----------------------------------------------------------------------

    private void shipment(UUID warehouseId, String shippedAt, int qty) {
        Timestamp at = Timestamp.from(Instant.parse(shippedAt));
        jdbc.update("INSERT INTO admin_shipment_summary "
                        + "(shipment_id, order_id, warehouse_id, shipped_at, total_qty, last_event_at, version, tenant_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 0, 'demo-corp')",
                UUID.randomUUID(), UUID.randomUUID(), warehouseId, at, qty, at);
    }

    private void fact(UUID warehouseId, String completedAt, int qty) {
        putawayFactRepo.insertIfAbsent(UUID.randomUUID(), warehouseId, Instant.parse(completedAt), qty);
    }

    private void outboundDaily(LocalDate date, UUID warehouseId, int count, int qty) {
        jdbc.update("INSERT INTO admin_throughput_outbound_daily "
                        + "(date, warehouse_id, shipment_count, qty_shipped, last_event_at, version) "
                        + "VALUES (?, ?, ?, ?, now(), 1)",
                date, warehouseId, count, qty);
    }

    private void inboundDaily(LocalDate date, UUID warehouseId, int count, int qty) {
        jdbc.update("INSERT INTO admin_throughput_inbound_daily "
                        + "(date, warehouse_id, putaway_count, qty_received, last_event_at, version) "
                        + "VALUES (?, ?, ?, ?, now(), 1)",
                date, warehouseId, count, qty);
    }

    private List<Integer> outboundRow(LocalDate date, UUID warehouseId) {
        return jdbc.query("SELECT shipment_count, qty_shipped FROM admin_throughput_outbound_daily "
                        + "WHERE date = ? AND warehouse_id = ?",
                (rs, i) -> List.of(rs.getInt(1), rs.getInt(2)), date, warehouseId)
                .stream().findFirst().orElse(List.of());
    }

    private List<Integer> inboundRow(LocalDate date, UUID warehouseId) {
        return jdbc.query("SELECT putaway_count, qty_received FROM admin_throughput_inbound_daily "
                        + "WHERE date = ? AND warehouse_id = ?",
                (rs, i) -> List.of(rs.getInt(1), rs.getInt(2)), date, warehouseId)
                .stream().findFirst().orElse(List.of());
    }
}
