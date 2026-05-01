package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.CloseAsnResult;
import java.time.Instant;
import java.util.UUID;

public record CloseAsnResponse(
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

    public static CloseAsnResponse from(CloseAsnResult r) {
        return new CloseAsnResponse(
                r.asnId(), r.asnNo(), r.status(), r.closedAt(), r.closedBy(),
                new Summary(r.summary().expectedTotal(), r.summary().passedTotal(),
                        r.summary().damagedTotal(), r.summary().shortTotal(),
                        r.summary().putawayConfirmedTotal(), r.summary().discrepancyCount()),
                r.version());
    }
}
