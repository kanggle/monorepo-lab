package com.wms.admin.application.dashboard;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import com.wms.admin.readmodel.outbound.ShipmentSummaryRepository;
import com.wms.admin.readmodel.throughput.PutawayFactRepository;
import com.wms.admin.readmodel.throughput.TimedQuantity;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code admin-service-api.md § 1.2} {@code GET /dashboard/metrics} — the WMS row of the
 * performance strip (ADR-MONO-084 P3, TASK-MONO-808). Same definitions as § 1.2
 * throughput, on the KST calendar.
 *
 * <p>🔴 Computed from per-event rows ({@code admin_putaway_fact} ·
 * {@code admin_shipment_summary}), never from the daily counters: the previous window
 * ends at «the same elapsed time» ({@link KstPeriodBounds#previousWindow}), which a
 * date-only row cannot cut.
 *
 * <p>Warehouse-global (contract § 1.0) — no tenant filter, like throughput.
 */
@Service
public class WarehouseMetricsService {

    public static final String METRIC_QTY_RECEIVED = "qtyReceived";
    public static final String METRIC_QTY_SHIPPED = "qtyShipped";
    public static final String METRIC_SHIPMENT_COUNT = "shipmentCount";

    private final PutawayFactRepository putawayFactRepo;
    private final ShipmentSummaryRepository shipmentRepo;
    private final Clock clock;

    public WarehouseMetricsService(PutawayFactRepository putawayFactRepo,
                                   ShipmentSummaryRepository shipmentRepo,
                                   Clock clock) {
        this.putawayFactRepo = putawayFactRepo;
        this.shipmentRepo = shipmentRepo;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PerformanceMetrics getMetrics(MetricsPeriod period, UUID warehouseId) {
        KstPeriodBounds bounds = KstPeriodBounds.from(clock);
        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        List<TimedQuantity> putaways = putawayFactRepo.findInWindow(
                warehouseId, current.fromInstant(), current.toInstant());
        List<TimedQuantity> previousPutaways = putawayFactRepo.findInWindow(
                warehouseId, previous.fromInstant(), previous.toInstant());
        List<TimedQuantity> shipments = shipmentRepo.findShippedInWindow(
                warehouseId, current.fromInstant(), current.toInstant());
        List<TimedQuantity> previousShipments = shipmentRepo.findShippedInWindow(
                warehouseId, previous.fromInstant(), previous.toInstant());

        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        for (TimedQuantity p : putaways) {
            day(daily, p).merge(METRIC_QTY_RECEIVED, p.qty(), Long::sum);
        }
        for (TimedQuantity s : shipments) {
            Map<String, Long> day = day(daily, s);
            day.merge(METRIC_QTY_SHIPPED, s.qty(), Long::sum);
            day.merge(METRIC_SHIPMENT_COUNT, 1L, Long::sum);
        }

        return PerformanceMetrics.of(bounds, period, List.of(
                PerformanceMetrics.Metric.count(METRIC_QTY_RECEIVED, qty(putaways), qty(previousPutaways)),
                PerformanceMetrics.Metric.count(METRIC_QTY_SHIPPED, qty(shipments), qty(previousShipments)),
                PerformanceMetrics.Metric.count(METRIC_SHIPMENT_COUNT, shipments.size(), previousShipments.size())),
                daily);
    }

    private static Map<String, Long> day(Map<LocalDate, Map<String, Long>> daily, TimedQuantity row) {
        return daily.computeIfAbsent(KstPeriodBounds.dateOf(row.at()), d -> new HashMap<>());
    }

    private static long qty(List<TimedQuantity> rows) {
        long sum = 0;
        for (TimedQuantity r : rows) {
            sum += r.qty();
        }
        return sum;
    }
}
