package com.wms.admin.api.dashboard.dto;

import java.time.Instant;
import java.util.List;

/** Response shape per {@code admin-service-api.md § 6.2}. */
public record ProjectionStatusResponse(
        List<ProjectionEntry> projections,
        double worstLagSeconds,
        long lifetimeApplied,
        long lifetimeIgnoredDuplicate,
        long lifetimeIgnoredDuplicateLate,
        long lifetimeFailed) {

    public record ProjectionEntry(
            String topic,
            String consumerGroup,
            double lagSeconds,
            Instant lastEventAt,
            Instant lastProjectedAt) {}
}
