package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ThroughputResponse;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyEntity;
import com.wms.admin.readmodel.throughput.ThroughputInboundDailyRepository;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyEntity;
import com.wms.admin.readmodel.throughput.ThroughputOutboundDailyRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code admin-service-api.md § 1.2} — daily inbound + outbound counters. */
@RestController
@RequestMapping("/api/v1/admin/dashboard/throughput")
@PreAuthorize("hasRole('WMS_VIEWER')")
public class ThroughputDashboardController {

    private static final int MAX_RANGE_DAYS = 90;

    private final ThroughputInboundDailyRepository inboundRepo;
    private final ThroughputOutboundDailyRepository outboundRepo;

    public ThroughputDashboardController(ThroughputInboundDailyRepository inboundRepo,
                                         ThroughputOutboundDailyRepository outboundRepo) {
        this.inboundRepo = inboundRepo;
        this.outboundRepo = outboundRepo;
    }

    @GetMapping
    public ThroughputResponse get(
            @RequestParam UUID warehouseId,
            @RequestParam String from,
            @RequestParam String to) {
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(from, DateTimeFormatter.ISO_LOCAL_DATE);
            toDate = LocalDate.parse(to, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("from/to must be ISO-8601 LocalDate (YYYY-MM-DD)");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        long span = java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        if (span > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("range exceeds " + MAX_RANGE_DAYS + " days");
        }

        List<ThroughputInboundDailyEntity> inbound = inboundRepo
                .findByWarehouseIdAndDateBetweenOrderByDateAsc(warehouseId, fromDate, toDate);
        List<ThroughputOutboundDailyEntity> outbound = outboundRepo
                .findByWarehouseIdAndDateBetweenOrderByDateAsc(warehouseId, fromDate, toDate);
        Map<LocalDate, ThroughputInboundDailyEntity> inboundByDate = new HashMap<>();
        for (var e : inbound) inboundByDate.put(e.getDate(), e);
        Map<LocalDate, ThroughputOutboundDailyEntity> outboundByDate = new HashMap<>();
        for (var e : outbound) outboundByDate.put(e.getDate(), e);

        TreeSet<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(inboundByDate.keySet());
        allDates.addAll(outboundByDate.keySet());

        List<ThroughputResponse.DayEntry> days = new ArrayList<>();
        int totalPutawayCount = 0;
        int totalQtyReceived = 0;
        int totalShipmentCount = 0;
        int totalQtyShipped = 0;
        for (LocalDate d : allDates) {
            ThroughputInboundDailyEntity ib = inboundByDate.get(d);
            ThroughputOutboundDailyEntity ob = outboundByDate.get(d);
            int pc = ib == null ? 0 : ib.getPutawayCount();
            int qr = ib == null ? 0 : ib.getQtyReceived();
            int sc = ob == null ? 0 : ob.getShipmentCount();
            int qs = ob == null ? 0 : ob.getQtyShipped();
            totalPutawayCount += pc;
            totalQtyReceived += qr;
            totalShipmentCount += sc;
            totalQtyShipped += qs;
            days.add(new ThroughputResponse.DayEntry(d,
                    new ThroughputResponse.InboundCounters(pc, qr),
                    new ThroughputResponse.OutboundCounters(sc, qs)));
        }
        return new ThroughputResponse(
                warehouseId, fromDate, toDate, days,
                new ThroughputResponse.Totals(
                        new ThroughputResponse.InboundCounters(totalPutawayCount, totalQtyReceived),
                        new ThroughputResponse.OutboundCounters(totalShipmentCount, totalQtyShipped)));
    }
}
