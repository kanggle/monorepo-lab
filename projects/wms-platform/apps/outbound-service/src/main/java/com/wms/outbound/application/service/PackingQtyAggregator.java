package com.wms.outbound.application.service;

import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitLine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates packed quantities by {@code orderLineId} across a list of
 * {@link PackingUnit}s.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link PackingService#allUnitsCoverAllLines} — compares the per-line
 *       packed sum against {@code OrderLine.qtyOrdered} during seal-time
 *       completion check.</li>
 *   <li>{@link PackingService#validatePackingCompleteness} — same map, strict
 *       equality assertion at confirm-time.</li>
 *   <li>{@link ConfirmShippingService#buildShippingEventLines} — safety map
 *       alongside the picking-confirmation source-of-truth.</li>
 * </ul>
 */
final class PackingQtyAggregator {

    private PackingQtyAggregator() {}

    /**
     * Returns a {@link Map} from {@code orderLineId} to the summed
     * {@code PackingUnitLine.qty} (as {@code Long}) across every
     * {@link PackingUnit} in {@code units}.
     *
     * <p>Behavior:
     * <ul>
     *   <li>Empty {@code units} → empty map.</li>
     *   <li>Iteration order is implementation-defined ({@link HashMap}).</li>
     *   <li>Each {@code PackingUnitLine.qty} is widened to {@code long}
     *       individually so a 1 000-line order with int-max quantities still
     *       sums cleanly.</li>
     * </ul>
     */
    static Map<UUID, Long> sumByOrderLine(List<PackingUnit> units) {
        Map<UUID, Long> sumByOrderLine = new HashMap<>();
        for (PackingUnit u : units) {
            for (PackingUnitLine l : u.getLines()) {
                sumByOrderLine.merge(l.getOrderLineId(), (long) l.getQty(), Long::sum);
            }
        }
        return sumByOrderLine;
    }
}
