package com.wms.admin.api.dashboard.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Throughput response per {@code admin-service-api.md § 1.2}. */
public record ThroughputResponse(
        UUID warehouseId,
        LocalDate from,
        LocalDate to,
        List<DayEntry> days,
        Totals totals) {

    public record DayEntry(LocalDate date, InboundCounters inbound, OutboundCounters outbound) {}

    public record InboundCounters(int putawayCount, int qtyReceived) {}

    public record OutboundCounters(int shipmentCount, int qtyShipped) {}

    public record Totals(InboundCounters inbound, OutboundCounters outbound) {}
}
