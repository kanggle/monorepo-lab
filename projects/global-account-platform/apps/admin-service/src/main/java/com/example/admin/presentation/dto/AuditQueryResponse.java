package com.example.admin.presentation.dto;

import com.example.admin.application.AuditQueryResult;

import java.time.Instant;
import java.util.List;

public record AuditQueryResponse(
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

    public static AuditQueryResponse from(AuditQueryResult r) {
        List<Entry> entries = r.content().stream()
                .map(e -> new Entry(
                        e.source(), e.auditId(), e.eventId(), e.actionCode(),
                        e.operatorId(), e.accountId(), e.targetId(), e.reason(),
                        e.outcome(), e.ipMasked(), e.geoCountry(), e.occurredAt()))
                .toList();
        return new AuditQueryResponse(entries, r.page(), r.size(), r.totalElements(), r.totalPages());
    }
}
