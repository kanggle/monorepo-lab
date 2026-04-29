package com.wms.inbound.application.result;

import java.time.Instant;
import java.util.UUID;

public record CloseAsnResult(
        UUID asnId,
        String asnNo,
        String status,
        Instant closedAt,
        String closedBy,
        Summary summary,
        long version
) {
    public record Summary(
            int expectedTotal,
            int passedTotal,
            int damagedTotal,
            int shortTotal,
            int putawayConfirmedTotal,
            int discrepancyCount
    ) {}
}
