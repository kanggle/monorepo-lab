package com.example.admin.application;

import java.time.Instant;
import java.util.List;

public record AuditQueryResult(
        List<Entry> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record Entry(
            String source,
            String auditId,
            String eventId,
            String actionCode,
            String operatorId,
            String accountId,
            String targetId,
            String reason,
            String outcome,
            String ipMasked,
            String geoCountry,
            Instant occurredAt
    ) {}
}
