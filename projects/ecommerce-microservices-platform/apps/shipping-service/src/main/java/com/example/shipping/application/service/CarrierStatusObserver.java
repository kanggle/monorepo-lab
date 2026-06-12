package com.example.shipping.application.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Observability seam for aggregator status mapping (TASK-BE-362 / ADR-007 F1).
 *
 * <p>{@link CarrierStatusMapper} is pure and silently maps an unknown aggregator status to
 * {@link java.util.Optional#empty()} (a no-op). On its own that risks a <b>silent stall</b>:
 * a new or changed aggregator unified-status code would just stop advancing a shipment with
 * no operator-visible signal ("왜 안 움직이지"). This observer makes the unmapped case visible:
 * the callers (refresh pull / inbound webhook) invoke {@link #recordUnmapped} whenever a
 * <em>non-blank</em> aggregator status fails to map, incrementing the
 * {@code carrier_status_unmapped} counter (tagged with the raw status) and emitting a WARN
 * log carrying the raw value. The fix is then "add the code to the mapping table".
 *
 * <p>Keeping the meter registration here (not in {@link CarrierStatusMapper}) preserves the
 * mapper as a pure function and matches the application-layer wiring point where the mapping
 * is consumed.
 */
@Slf4j
@Component
public class CarrierStatusObserver {

    /** Micrometer counter name for non-blank aggregator statuses that failed to map. */
    static final String UNMAPPED_COUNTER = "carrier_status_unmapped";

    private final MeterRegistry meterRegistry;

    public CarrierStatusObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record that a non-blank aggregator status failed to map to a domain status. Blank /
     * null statuses are <b>not</b> recorded (they carry no signal — net-zero). The raw status
     * is attached as a {@code raw_status} tag and logged; {@code source} distinguishes the
     * pull ({@code refresh}) from the inbound ({@code webhook}) path.
     */
    public void recordUnmapped(String source, String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return;
        }
        meterRegistry.counter(UNMAPPED_COUNTER, "source", source, "raw_status", rawStatus).increment();
        log.warn("Aggregator reported unmapped status '{}' via {}; no-op (add it to CarrierStatusMapper)",
                rawStatus, source);
    }
}
