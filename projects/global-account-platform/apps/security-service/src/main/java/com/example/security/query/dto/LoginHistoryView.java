package com.example.security.query.dto;

import java.time.Instant;

public record LoginHistoryView(
        String eventId,
        String accountId,
        String outcome,
        String ipMasked,
        String userAgentFamily,
        String deviceFingerprint,
        String geoCountry,
        Instant occurredAt
) {
}
