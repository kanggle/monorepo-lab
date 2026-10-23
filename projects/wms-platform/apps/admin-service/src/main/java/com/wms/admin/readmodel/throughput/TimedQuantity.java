package com.wms.admin.readmodel.throughput;

import java.time.Instant;

/** One putaway or shipment reduced to the two things the performance metrics need. */
public record TimedQuantity(Instant at, long qty) {

    public TimedQuantity(Instant at, int qty) {
        this(at, (long) qty);
    }
}
